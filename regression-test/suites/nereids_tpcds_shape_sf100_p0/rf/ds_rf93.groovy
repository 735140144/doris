/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

suite("ds_rf93") {
    String db = context.config.getDbNameByFile(new File(context.file.parent))
    sql "use ${db}"
    sql 'set enable_nereids_planner=true'
    sql 'set enable_fallback_to_original_planner=false'
    sql 'set exec_mem_limit=21G'
    sql 'set be_number_for_test=3'
    sql 'set parallel_fragment_exec_instance_num=8; '
    sql 'set parallel_pipeline_task_num=8; '
    sql 'set forbid_unknown_col_stats=true'
    sql 'set broadcast_row_count_limit = 30000000'
    sql 'set enable_nereids_timeout = false'
    sql 'set enable_pipeline_engine=true'
    String stmt = '''
    explain physical plan
    select  ss_customer_sk
            ,sum(act_sales) sumsales
      from (select ss_item_sk
                  ,ss_ticket_number
                  ,ss_customer_sk
                  ,case when sr_return_quantity is not null then (ss_quantity-sr_return_quantity)*ss_sales_price
                                                            else (ss_quantity*ss_sales_price) end act_sales
            from store_sales left outer join store_returns on (sr_item_sk = ss_item_sk
                                                               and sr_ticket_number = ss_ticket_number)
                ,reason
            where sr_reason_sk = r_reason_sk
              and r_reason_desc = 'duplicate purchase') t
      group by ss_customer_sk
      order by sumsales, ss_customer_sk
limit 100;

    '''
    String plan = sql "${stmt}"
    log.info(plan)
    def getRuntimeFilters = { plantree ->
        {
            def lst = []
            plantree.eachMatch("RF\\d+\\[[^#]+#\\d+->\\[[^\\]]+\\]") {
                ch ->
                    {
                        lst.add(ch.replaceAll("#\\d+", ''))
                    }
            }
            return lst.join(',')
        }
    }
    
    // def outFile = "regression-test/suites/nereids_tpcds_shape_sf100_p0/ddl/rf/rf.93"
    // File file = new File(outFile)
    // file.write(getRuntimeFilters(plan))
    
     assertEquals("RF1[sr_item_sk->[ss_item_sk],RF2[sr_ticket_number->[ss_ticket_number],RF0[r_reason_sk->[sr_reason_sk]", getRuntimeFilters(plan))
}
