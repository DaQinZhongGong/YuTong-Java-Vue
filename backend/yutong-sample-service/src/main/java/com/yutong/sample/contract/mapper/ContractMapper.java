package com.yutong.sample.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.sample.contract.domain.Contract;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 合同 Mapper。设计来源: 35-样例业务矩阵扩展设计 P1 合同档案。
 *
 * <p>支持 PG 全文检索查询（search_vector GIN 索引）。
 */
@Mapper
public interface ContractMapper extends BaseMapper<Contract> {

    /**
     * 全文检索查询合同。
     *
     * <p>采用「tsvector @@ tsquery + ILIKE 兜底」混合策略:
     * <ul>
     *   <li>PG simple 配置对中文不分词，整串作为单个 token，"技术服务" 无法命中 tsvector 中的 "年度技术服务框架合同" token</li>
     *   <li>因此对 title/contract_no/party_a/party_b/content_summary 增加 ILIKE 子串兜底</li>
     *   <li>仍用 ts_rank 排序（命中全文检索的优先），GIN 索引验证保留</li>
     * </ul>
     *
     * @param tenantId 租户
     * @param query    查询字符串（中文/英文均支持）
     * @param limit    返回条数上限
     * @return 命中合同列表（按 ts_rank 排序）
     */
    @Select("SELECT * FROM contract " +
            "WHERE tenant_id = #{tenantId} AND deleted = false " +
            "AND (search_vector @@ plainto_tsquery('simple', #{query}) " +
            "     OR title ILIKE '%' || #{query} || '%' " +
            "     OR contract_no ILIKE '%' || #{query} || '%' " +
            "     OR party_a ILIKE '%' || #{query} || '%' " +
            "     OR party_b ILIKE '%' || #{query} || '%' " +
            "     OR content_summary ILIKE '%' || #{query} || '%') " +
            "ORDER BY ts_rank(search_vector, plainto_tsquery('simple', #{query})) DESC, created_time DESC " +
            "LIMIT #{limit}")
    List<Contract> searchByFullText(@Param("tenantId") String tenantId,
                                     @Param("query") String query,
                                     @Param("limit") int limit);
}
