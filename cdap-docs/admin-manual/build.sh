#!/usr/bin/env bash

# Copyright © 2014-2015 Cask Data, Inc.
# 
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
# 
# http://www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.
  
# Build script for docs

source ../_common/common-build.sh

DEFAULT_XML="../../cdap-common/src/main/resources/cdap-default.xml"
DEFAULT_XML_MD5_HASH="e8cc6ab35e17a8c28adafb02d65027b5"

DEFAULT_TOOL="../tools/doc-cdap-default.py"
DEFAULT_RST="cdap-default-table.rst"
CHECK_INCLUDES=${TRUE}

function rewrite_references_sed() {
  local source_rst=${1}
  local target_rst=${2}
  local source_pattern=${3}
  local target_pattern=${4}
  sed -e "s|${source_pattern}|${target_pattern}|g" ${source_rst} > ${target_rst}
  echo "Copied file ${source_rst} changing '${source_pattern}' to '${target_pattern}'"
}

function download_includes() {
  local target_includes_dir=${1}

  echo_red_bold "Check guarded files for changes."
  test_an_include "${DEFAULT_XML_MD5_HASH}" "${DEFAULT_XML}"

  echo "Building rst file from cdap-default.xml..." 
  python "${DEFAULT_TOOL}" -g -t "${target_includes_dir}/${DEFAULT_RST}"
  
  echo "Copying files, changing references..."
  local source_rst="${target_includes_dir}/../../source/_includes/installation"
  local pattern="\|distribution\|"  
  local distributions="ambari mapr package-managers"
  local types="configuration installation starting upgrading"
  for dist in ${distributions}; do
    for type in ${types}; do
      rewrite_references_sed "${source_rst}/${type}.txt" "${target_includes_dir}/${dist}-${type}.rst" "${pattern}" "${dist}"
    done
    echo
  done
  
  echo "Providing distribution-specific cdap-site.xml.example files"
  local source_xml="../../cdap-distributions/src/etc/cdap/conf.dist/cdap-site.xml.example"
  local distributions="mapr package-managers"
  for dist in ${distributions}; do
    case "${dist}" in
      mapr             ) local hdfs_user="cdap";;
      package-managers ) local hdfs_user="yarn";;
    esac
    rewrite_references_sed "${source_xml}" "${target_includes_dir}/${dist}-cdap-site.xml.example" "<value>yarn</value>" "<value>${hdfs_user}</value>"
  done
}

run_command ${1}
