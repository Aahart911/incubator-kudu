// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
#ifndef KUDU_CLIENT_TABLE_CREATOR_INTERNAL_H
#define KUDU_CLIENT_TABLE_CREATOR_INTERNAL_H

#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "kudu/client/client.h"
#include "kudu/common/common.pb.h"

namespace kudu {

namespace client {

class KuduTableCreator::Data {
 public:
  explicit Data(KuduClient* client);
  ~Data();

  KuduClient* client_;

  std::string table_name_;

  const KuduSchema* schema_;

  std::vector<std::unique_ptr<KuduPartialRow>> range_splits_;

  std::vector<std::pair<std::unique_ptr<KuduPartialRow>,
                         std::unique_ptr<KuduPartialRow>>> range_bounds_;

  PartitionSchemaPB partition_schema_;

  int num_replicas_;

  MonoDelta timeout_;

  bool wait_;

  DISALLOW_COPY_AND_ASSIGN(Data);
};

} // namespace client
} // namespace kudu

#endif
