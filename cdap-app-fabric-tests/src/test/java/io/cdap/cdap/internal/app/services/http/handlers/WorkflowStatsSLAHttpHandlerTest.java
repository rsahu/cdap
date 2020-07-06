/*
 * Copyright © 2016-2018 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.cdap.cdap.internal.app.services.http.handlers;

import com.google.common.collect.ImmutableMap;
import com.google.gson.reflect.TypeToken;
import io.cdap.cdap.WorkflowApp;
import io.cdap.cdap.api.artifact.ArtifactId;
import io.cdap.cdap.api.metrics.MetricStore;
import io.cdap.cdap.api.metrics.MetricType;
import io.cdap.cdap.api.metrics.MetricValues;
import io.cdap.cdap.app.metrics.MapReduceMetrics;
import io.cdap.cdap.app.store.Store;
import io.cdap.cdap.common.app.RunIds;
import io.cdap.cdap.common.conf.Constants;
import io.cdap.cdap.common.id.Id;
import io.cdap.cdap.gateway.handlers.WorkflowStatsSLAHttpHandler;
import io.cdap.cdap.internal.AppFabricTestHelper;
import io.cdap.cdap.internal.app.runtime.ProgramOptionConstants;
import io.cdap.cdap.internal.app.runtime.SystemArguments;
import io.cdap.cdap.internal.app.services.http.AppFabricTestBase;
import io.cdap.cdap.internal.app.store.DefaultStore;
import io.cdap.cdap.proto.PercentileInformation;
import io.cdap.cdap.proto.ProgramRunStatus;
import io.cdap.cdap.proto.ProgramType;
import io.cdap.cdap.proto.WorkflowStatistics;
import io.cdap.cdap.proto.WorkflowStatsComparison;
import io.cdap.cdap.proto.id.ApplicationId;
import io.cdap.cdap.proto.id.NamespaceId;
import io.cdap.cdap.proto.id.ProfileId;
import io.cdap.cdap.proto.id.ProgramId;
import io.cdap.cdap.proto.id.WorkflowId;
import io.cdap.common.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.hadoop.mapreduce.TaskCounter;
import org.apache.twill.api.RunId;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link WorkflowStatsSLAHttpHandler}
 */
public class WorkflowStatsSLAHttpHandlerTest extends AppFabricTestBase {

  private static final ApplicationId WORKFLOW_APP = NamespaceId.DEFAULT.app("WorkflowApp");
  private static MetricStore metricStore;
  private static Store store;

  private int sourceId;

  @BeforeClass
  public static void setup() {
    store = getInjector().getInstance(DefaultStore.class);
    metricStore = getInjector().getInstance(MetricStore.class);
  }

  private void setStartAndRunning(ProgramId id, String pid, ArtifactId artifactId) {
    setStartAndRunning(id, pid, ImmutableMap.of(), ImmutableMap.of(), artifactId);
  }

  private void setStartAndRunning(ProgramId id, String pid, Map<String, String> runtimeArgs,
                                  Map<String, String> systemArgs, ArtifactId artifactId) {
    if (!systemArgs.containsKey(SystemArguments.PROFILE_NAME)) {
      systemArgs = ImmutableMap.<String, String>builder()
        .putAll(systemArgs)
        .put(SystemArguments.PROFILE_NAME, ProfileId.NATIVE.getScopedName())
        .build();
    }
    long startTime = RunIds.getTime(pid, TimeUnit.SECONDS);
    store.setProvisioning(id.run(pid), runtimeArgs, systemArgs,
                          AppFabricTestHelper.createSourceId(++sourceId), artifactId);
    store.setProvisioned(id.run(pid), 0, AppFabricTestHelper.createSourceId(++sourceId));
    store.setStart(id.run(pid), null, systemArgs, AppFabricTestHelper.createSourceId(++sourceId));
    store.setRunning(id.run(pid), startTime + 1, null, AppFabricTestHelper.createSourceId(++sourceId));
  }

  @Test
  public void testStatistics() throws Exception {
    deploy(WorkflowApp.class, 200);
    String workflowName = "FunWorkflow";
    String mapreduceName = "ClassicWordCount";
    String sparkName = "SparkWorkflowTest";

    ProgramId workflowProgram = WORKFLOW_APP.workflow(workflowName);
    ProgramId mapreduceProgram = WORKFLOW_APP.mr(mapreduceName);
    ProgramId sparkProgram = WORKFLOW_APP.spark(sparkName);
    ArtifactId artifactId = WORKFLOW_APP.getNamespaceId().artifact("testArtifact", "1.0").toApiArtifactId();

    long startTime = System.currentTimeMillis();
    long currentTimeMillis = startTime;
    String outlierRunId = null;
    for (int i = 0; i < 10; i++) {
      // workflow runs every 5 minutes
      currentTimeMillis = startTime + (i * TimeUnit.MINUTES.toMillis(5));
      RunId workflowRunId = RunIds.generate(currentTimeMillis);
      setStartAndRunning(workflowProgram, workflowRunId.getId(), artifactId);

      // MR job starts 2 seconds after workflow started
      RunId mapreduceRunid = RunIds.generate(currentTimeMillis + TimeUnit.SECONDS.toMillis(2));
      Map<String, String> systemArgs = ImmutableMap.of(ProgramOptionConstants.WORKFLOW_NODE_ID, mapreduceName,
                                                       ProgramOptionConstants.WORKFLOW_NAME, workflowName,
                                                       ProgramOptionConstants.WORKFLOW_RUN_ID, workflowRunId.getId());

      setStartAndRunning(mapreduceProgram, mapreduceRunid.getId(), ImmutableMap.of(), systemArgs, artifactId);

      store.setStop(mapreduceProgram.run(mapreduceRunid),
                    // map-reduce job ran for 17 seconds
                    TimeUnit.MILLISECONDS.toSeconds(currentTimeMillis) + 19,
                    ProgramRunStatus.COMPLETED, AppFabricTestHelper.createSourceId(++sourceId));

      // This makes sure that not all runs have Spark programs in them
      if (i < 5) {
        // spark starts 20 seconds after workflow starts
        RunId sparkRunid = RunIds.generate(currentTimeMillis + TimeUnit.SECONDS.toMillis(20));
        systemArgs = ImmutableMap.of(ProgramOptionConstants.WORKFLOW_NODE_ID, sparkProgram.getProgram(),
                                     ProgramOptionConstants.WORKFLOW_NAME, workflowName,
                                     ProgramOptionConstants.WORKFLOW_RUN_ID, workflowRunId.getId());
        setStartAndRunning(sparkProgram, sparkRunid.getId(), ImmutableMap.of(), systemArgs, artifactId);

        // spark job runs for 38 seconds
        long stopTime = TimeUnit.MILLISECONDS.toSeconds(currentTimeMillis) + 58;
        if (i == 4) {
          // spark job ran for 100 seconds. 62 seconds greater than avg.
          stopTime = TimeUnit.MILLISECONDS.toSeconds(currentTimeMillis) + 120;
        }
        store.setStop(sparkProgram.run(sparkRunid.getId()), stopTime, ProgramRunStatus.COMPLETED,
                      AppFabricTestHelper.createSourceId(++sourceId));
      }

      // workflow ran for 1 minute
      long workflowStopTime = TimeUnit.MILLISECONDS.toSeconds(currentTimeMillis) + 60;
      if (i == 4) {
        // spark job ran longer for this run
        workflowStopTime = TimeUnit.MILLISECONDS.toSeconds(currentTimeMillis) + 122;
        outlierRunId = workflowRunId.getId();
      }

      store.setStop(workflowProgram.run(workflowRunId.getId()), workflowStopTime, ProgramRunStatus.COMPLETED,
                    AppFabricTestHelper.createSourceId(++sourceId));
    }

    String request = String.format("%s/namespaces/%s/apps/%s/workflows/%s/statistics?start=%s&end=%s" +
                                     "&percentile=%s",
                                   Constants.Gateway.API_VERSION_3, Id.Namespace.DEFAULT.getId(),
                                   WorkflowApp.class.getSimpleName(), workflowProgram.getProgram(),
                                   TimeUnit.MILLISECONDS.toSeconds(startTime),
                                   TimeUnit.MILLISECONDS.toSeconds(currentTimeMillis) + TimeUnit.MINUTES.toSeconds(2),
                                   "99");

    HttpResponse response = doGet(request);
    WorkflowStatistics workflowStatistics =
      readResponse(response, new TypeToken<WorkflowStatistics>() { }.getType());
    PercentileInformation percentileInformation = workflowStatistics.getPercentileInformationList().get(0);
    Assert.assertEquals(1, percentileInformation.getRunIdsOverPercentile().size());
    Assert.assertEquals(outlierRunId, percentileInformation.getRunIdsOverPercentile().get(0));
    Assert.assertEquals("5", workflowStatistics.getNodes().get(sparkName).get("runs"));

    request = String.format("%s/namespaces/%s/apps/%s/workflows/%s/statistics?start=%s&end=%s" +
                              "&percentile=%s&percentile=%s",
                            Constants.Gateway.API_VERSION_3, Id.Namespace.DEFAULT.getId(),
                            WorkflowApp.class.getSimpleName(), workflowProgram.getProgram(), "now", "0", "90", "95");

    response = doGet(request);
    Assert.assertEquals(HttpResponseStatus.BAD_REQUEST.code(),
                        response.getResponseCode());

    request = String.format("%s/namespaces/%s/apps/%s/workflows/%s/statistics?start=%s&end=%s" +
                              "&percentile=%s&percentile=%s",
                            Constants.Gateway.API_VERSION_3, Id.Namespace.DEFAULT.getId(),
                            WorkflowApp.class.getSimpleName(), workflowProgram.getProgram(), "now", "0", "90.0", "950");

    response = doGet(request);
    Assert.assertEquals(HttpResponseStatus.BAD_REQUEST.code(),
                        response.getResponseCode());
    Id.Application appId = new Id.Application(Id.Namespace.DEFAULT, WorkflowApp.class.getSimpleName());
    deleteApp(appId, HttpResponseStatus.OK.code());

    request = String.format("%s/namespaces/%s/apps/%s/workflows/%s/statistics?start=%s&end=%s" +
                              "&percentile=%s",
                            Constants.Gateway.API_VERSION_3, Id.Namespace.DEFAULT.getId(),
                            WorkflowApp.class.getSimpleName(), workflowProgram,
                            0,
                            System.currentTimeMillis(),
                            "99");
    response = doGet(request);
    Assert.assertEquals(HttpResponseStatus.OK.code(), response.getResponseCode());
    Assert.assertTrue(
      response.getResponseBodyAsString().startsWith("There are no statistics associated with this workflow : "));
  }


  @Test
  public void testDetails() throws Exception {
    deploy(WorkflowApp.class, 200);
    String workflowName = "FunWorkflow";
    String mapreduceName = "ClassicWordCount";
    String sparkName = "SparkWorkflowTest";

    WorkflowId workflowProgram = WORKFLOW_APP.workflow(workflowName);
    ProgramId mapreduceProgram = WORKFLOW_APP.mr(mapreduceName);
    ProgramId sparkProgram = WORKFLOW_APP.spark(sparkName);
    ArtifactId artifactId = WORKFLOW_APP.getNamespaceId().artifact("testArtifact", "1.0").toApiArtifactId();
    List<RunId> runIdList = setupRuns(workflowProgram, mapreduceProgram, sparkProgram, store, 13, artifactId);

    String request = String.format("%s/namespaces/%s/apps/%s/workflows/%s/runs/%s/statistics?limit=%s&interval=%s",
                                   Constants.Gateway.API_VERSION_3, Id.Namespace.DEFAULT.getId(),
                                   WorkflowApp.class.getSimpleName(), workflowProgram.getProgram(),
                                   runIdList.get(6).getId(), "3", "10m");

    HttpResponse response = doGet(request);
    WorkflowStatsComparison workflowStatistics =
      readResponse(response, new TypeToken<WorkflowStatsComparison>() { }.getType());

    Assert.assertEquals(7, workflowStatistics.getProgramNodesList().iterator().next()
      .getWorkflowProgramDetailsList().size());

    request = String.format("%s/namespaces/%s/apps/%s/workflows/%s/runs/%s/statistics?limit=0",
                            Constants.Gateway.API_VERSION_3, Id.Namespace.DEFAULT.getId(),
                            WorkflowApp.class.getSimpleName(), workflowProgram.getProgram(), runIdList.get(6).getId());

    response = doGet(request);
    Assert.assertEquals(HttpResponseStatus.BAD_REQUEST.code(), response.getResponseCode());

    request = String.format("%s/namespaces/%s/apps/%s/workflows/%s/runs/%s/statistics?limit=10&interval=10",
                            Constants.Gateway.API_VERSION_3, Id.Namespace.DEFAULT.getId(),
                            WorkflowApp.class.getSimpleName(), workflowProgram.getProgram(), runIdList.get(6).getId());

    response = doGet(request);
    Assert.assertEquals(HttpResponseStatus.BAD_REQUEST.code(), response.getResponseCode());

    request = String.format("%s/namespaces/%s/apps/%s/workflows/%s/runs/%s/statistics?limit=10&interval=10P",
                            Constants.Gateway.API_VERSION_3, Id.Namespace.DEFAULT.getId(),
                            WorkflowApp.class.getSimpleName(), workflowProgram.getProgram(), runIdList.get(6).getId());

    response = doGet(request);
    Assert.assertEquals(HttpResponseStatus.BAD_REQUEST.code(), response.getResponseCode());

    request = String.format("%s/namespaces/%s/apps/%s/workflows/%s/runs/%s/statistics?limit=20&interval=0d",
                            Constants.Gateway.API_VERSION_3, Id.Namespace.DEFAULT.getId(),
                            WorkflowApp.class.getSimpleName(), workflowProgram.getProgram(), runIdList.get(6).getId());

    response = doGet(request);
    Assert.assertEquals(HttpResponseStatus.BAD_REQUEST.code(), response.getResponseCode());
  }

  @Test
  public void testCompare() throws Exception {
    deploy(WorkflowApp.class, 200);
    String workflowName = "FunWorkflow";
    String mapreduceName = "ClassicWordCount";
    String sparkName = "SparkWorkflowTest";

    WorkflowId workflowProgram = WORKFLOW_APP.workflow(workflowName);
    ProgramId mapreduceProgram = WORKFLOW_APP.mr(mapreduceName);
    ProgramId sparkProgram = WORKFLOW_APP.spark(sparkName);
    ArtifactId artifactId = WORKFLOW_APP.getNamespaceId().artifact("testArtifact", "1.0").toApiArtifactId();

    List<RunId> workflowRunIdList = setupRuns(workflowProgram, mapreduceProgram, sparkProgram, store, 2, artifactId);
    RunId workflowRun1 = workflowRunIdList.get(0);
    RunId workflowRun2 = workflowRunIdList.get(1);

    String request = String.format("%s/namespaces/%s/apps/%s/workflows/%s/runs/%s/compare?other-run-id=%s",
                                   Constants.Gateway.API_VERSION_3, Id.Namespace.DEFAULT.getId(),
                                   WorkflowApp.class.getSimpleName(), workflowProgram.getProgram(),
                                   workflowRun1.getId(), workflowRun2.getId());

    HttpResponse response = doGet(request);
    Collection<WorkflowStatsComparison.ProgramNodes> workflowStatistics =
      readResponse(response, new TypeToken<Collection<WorkflowStatsComparison.ProgramNodes>>() {
      }.getType());

    Assert.assertNotNull(workflowStatistics.iterator().next());
    Assert.assertEquals(2, workflowStatistics.size());

    for (WorkflowStatsComparison.ProgramNodes node : workflowStatistics) {
      if (node.getProgramType() == ProgramType.MAPREDUCE) {
        Assert.assertEquals(38L, (long) node.getWorkflowProgramDetailsList().get(0)
          .getMetrics().get(TaskCounter.MAP_INPUT_RECORDS.name()));
      }
    }
  }

  /*
   * This helper is used only for the details and compare endpoints and not the statistics endpoint because
   * the statistics endpoint needs to handle number of spark runs differently and also have tests for a
   * specific run's spark job.
   */
  private List<RunId> setupRuns(WorkflowId workflowProgram, ProgramId mapreduceProgram,
                                ProgramId sparkProgram, Store store, int count, ArtifactId artifactId) {
    List<RunId> runIdList = new ArrayList<>();
    long startTime = System.currentTimeMillis();
    long currentTimeMillis;

    for (int i = 0; i < count; i++) {
      // work-flow runs every 5 minutes
      currentTimeMillis = startTime + (i * TimeUnit.MINUTES.toMillis(5));
      RunId workflowRunId = RunIds.generate(currentTimeMillis);
      runIdList.add(workflowRunId);
      setStartAndRunning(workflowProgram, workflowRunId.getId(), artifactId);

      // MR job starts 2 seconds after workflow started
      RunId mapreduceRunid = RunIds.generate(currentTimeMillis + TimeUnit.SECONDS.toMillis(2));
      Map<String, String> systemArgs = ImmutableMap.of(ProgramOptionConstants.WORKFLOW_NODE_ID,
                                                       mapreduceProgram.getProgram(),
                                                       ProgramOptionConstants.WORKFLOW_NAME,
                                                       workflowProgram.getProgram(),
                                                       ProgramOptionConstants.WORKFLOW_RUN_ID, workflowRunId.getId());

      setStartAndRunning(mapreduceProgram, mapreduceRunid.getId(), ImmutableMap.of(), systemArgs, artifactId);
      store.setStop(mapreduceProgram.run(mapreduceRunid.getId()),
                    // map-reduce job ran for 17 seconds
                    TimeUnit.MILLISECONDS.toSeconds(currentTimeMillis) + 19,
                    ProgramRunStatus.COMPLETED, AppFabricTestHelper.createSourceId(++sourceId));

      Map<String, String> mapTypeContext = ImmutableMap.of(Constants.Metrics.Tag.NAMESPACE,
                                                           mapreduceProgram.getNamespace(),
                                                           Constants.Metrics.Tag.APP,
                                                           mapreduceProgram.getApplication(),
                                                           Constants.Metrics.Tag.MAPREDUCE,
                                                           mapreduceProgram.getProgram(),
                                                           Constants.Metrics.Tag.RUN_ID, mapreduceRunid.toString(),
                                                           Constants.Metrics.Tag.MR_TASK_TYPE,
                                                           MapReduceMetrics.TaskType.Mapper.getId());

      metricStore.add(new MetricValues(mapTypeContext, MapReduceMetrics.METRIC_INPUT_RECORDS, 10, 38L,
                                       MetricType.GAUGE));

      // spark starts 20 seconds after workflow starts
      systemArgs = ImmutableMap.of(ProgramOptionConstants.WORKFLOW_NODE_ID, sparkProgram.getProgram(),
                                   ProgramOptionConstants.WORKFLOW_NAME, workflowProgram.getProgram(),
                                   ProgramOptionConstants.WORKFLOW_RUN_ID, workflowRunId.getId());
      RunId sparkRunid = RunIds.generate(currentTimeMillis + TimeUnit.SECONDS.toMillis(20));
      setStartAndRunning(sparkProgram, sparkRunid.getId(), ImmutableMap.of(), systemArgs, artifactId);

      // spark job runs for 38 seconds
      long stopTime = TimeUnit.MILLISECONDS.toSeconds(currentTimeMillis) + 58;
      store.setStop(sparkProgram.run(sparkRunid.getId()), stopTime, ProgramRunStatus.COMPLETED,
                    AppFabricTestHelper.createSourceId(++sourceId));

      // workflow ran for 1 minute
      long workflowStopTime = TimeUnit.MILLISECONDS.toSeconds(currentTimeMillis) + 60;

      store.setStop(workflowProgram.run(workflowRunId.getId()), workflowStopTime, ProgramRunStatus.COMPLETED,
                    AppFabricTestHelper.createSourceId(++sourceId));
    }
    return runIdList;
  }
}
