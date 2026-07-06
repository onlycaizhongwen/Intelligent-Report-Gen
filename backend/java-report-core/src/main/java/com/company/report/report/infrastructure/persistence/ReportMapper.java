package com.company.report.report.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ReportMapper {
    @Select("SELECT id, title, owner_user_id, status, current_version_id FROM reports WHERE id = #{id} AND deleted_at IS NULL")
    ReportPo findById(Long id);

    @Select("""
            SELECT id, title, owner_user_id, status, current_version_id
            FROM reports
            WHERE owner_user_id = #{ownerUserId}
              AND deleted_at IS NULL
            ORDER BY updated_at DESC, id DESC
            LIMIT #{pageSize} OFFSET #{offset}
            """)
    List<ReportPo> findByOwner(Long ownerUserId, int pageSize, long offset);

    @Select("""
            SELECT COUNT(*)
            FROM reports
            WHERE owner_user_id = #{ownerUserId}
              AND deleted_at IS NULL
            """)
    long countByOwner(Long ownerUserId);

    @Update("""
            UPDATE reports
            SET title = #{title},
                status = #{status},
                current_version_id = #{currentVersionId},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int update(ReportPo report);
}
