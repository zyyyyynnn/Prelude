package com.interview.platform.job.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.platform.job.AsyncJob;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AsyncJobMapper extends BaseMapper<AsyncJob> {
}
