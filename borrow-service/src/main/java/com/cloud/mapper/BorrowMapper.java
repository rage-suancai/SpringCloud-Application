package com.cloud.mapper;

import com.entity.Borrow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BorrowMapper {

    @Select("select id,bid,uid from db_borrow where uid = #{uid}")
    List<Borrow> getBorrowByUid(Integer uid);

    @Select("select id,bid,uid from db_borrow where bid = #{bid}")
    List<Borrow> getBorrowByBid(Integer bid);

}
