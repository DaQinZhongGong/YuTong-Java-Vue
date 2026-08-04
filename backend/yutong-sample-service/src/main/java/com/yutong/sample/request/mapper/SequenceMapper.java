package com.yutong.sample.request.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.sample.request.domain.Sequence;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SequenceMapper extends BaseMapper<Sequence> {
}
