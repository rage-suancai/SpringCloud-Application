package com.cloud.mapper;

import com.entity.Book;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BookMapper {

    @Select("select bid,title,`desc` from db_book where bid = #{bid}")
    Book getBookById(Integer bid);

}
