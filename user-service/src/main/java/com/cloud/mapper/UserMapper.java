package com.cloud.mapper;

import com.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    @Select("select uid,name,age,sex from db_user where uid = #{uid}")
    User getUserById(Integer uid);

}
