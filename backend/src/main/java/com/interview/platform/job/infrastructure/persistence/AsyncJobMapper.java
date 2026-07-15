package com.interview.platform.job.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AsyncJobMapper extends BaseMapper<AsyncJob> {
}
