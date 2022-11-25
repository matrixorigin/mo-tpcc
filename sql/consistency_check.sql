(Select w_id, w_ytd from bmsql_warehouse) except (select d_w_id, sum(d_ytd) from bmsql_district group by d_w_id);


(Select d_w_id, d_id, D_NEXT_O_ID - 1 from bmsql_district)  except (select o_w_id, o_d_id, max(o_id) from bmsql_oorder group by  o_w_id, o_d_id);


(Select d_w_id, d_id, D_NEXT_O_ID - 1 from bmsql_district)  except (select no_w_id, no_d_id, max(no_o_id) from bmsql_new_order group by no_w_id, no_d_id);


-- 执行语法报错
select * from (select (count(no_o_id)-(max(no_o_id)-min(no_o_id)+1)) as diff from bmsql_new_order group by no_w_id, no_d_id) as temp where diff != 0;


(select o_w_id, o_d_id, sum(o_ol_cnt) from bmsql_oorder  group by o_w_id, o_d_id) except (select ol_w_id, ol_d_id, count
(ol_o_id) from bmsql_order_line group by ol_w_id, ol_d_id);


(select d_w_id, sum(d_ytd) from bmsql_district group by d_w_id)  except(Select w_id, w_ytd from bmsql_warehouse);