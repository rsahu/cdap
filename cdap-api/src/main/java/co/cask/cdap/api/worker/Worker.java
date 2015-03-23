/*
 * Copyright © 2015 Cask Data, Inc.
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

package co.cask.cdap.api.worker;

import co.cask.cdap.api.ProgramLifecycle;

/**
 * Defines a Worker.
 */
public interface Worker extends Runnable, ProgramLifecycle<WorkerContext> {

  /**
   * Configure a Worker.
   */
  void configure(WorkerConfigurer configurer);

  /**
   * Logic executed when Worker is suspended.
   */
  void onSuspend();

  /**
   * Logic executed when Worker is resumed.
   */
  void onResume();

  /**
   * Request to stop the running worker.
   * This method will be invoked from a different thread than the one calling the {@link #run()} method.
   */
  void stop();
}
