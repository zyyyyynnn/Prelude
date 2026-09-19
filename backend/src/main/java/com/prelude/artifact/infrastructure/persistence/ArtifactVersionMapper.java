package com.prelude.artifact.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.prelude.artifact.domain.ArtifactVersion;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ArtifactVersionMapper extends BaseMapper<ArtifactVersion> {

    /**
     * The number the next version should claim. Computed in the database so a publisher
     * never loads the whole version list to find the tail; the unique constraint on
     * (artifact_id, version_number) stays the authority under concurrency.
     */
    @Select("SELECT COALESCE(MAX(version_number), 0) FROM artifact_version WHERE artifact_id = #{artifactId}")
    int highestVersionNumber(@Param("artifactId") Long artifactId);
}
