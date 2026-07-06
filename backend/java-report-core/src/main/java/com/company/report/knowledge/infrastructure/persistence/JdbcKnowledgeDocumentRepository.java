package com.company.report.knowledge.infrastructure.persistence;

import com.company.report.knowledge.domain.model.StoredDocument;
import com.company.report.knowledge.domain.repository.KnowledgeDocumentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcKnowledgeDocumentRepository implements KnowledgeDocumentRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcKnowledgeDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public StoredDocument saveUploadedDocument(UploadedDocumentRecord record) {
        Long fileObjectId = insertFileObject(record);
        Long documentId = insertKnowledgeDocument(record, fileObjectId);
        return new StoredDocument(
                documentId,
                fileObjectId,
                record.knowledgeBaseId(),
                record.fileName(),
                record.fileType(),
                "pending",
                record.bucket(),
                record.objectKey(),
                record.contentType(),
                record.sizeBytes()
        );
    }

    @Override
    public Optional<StoredDocument> findById(Long documentId) {
        return jdbcTemplate.query("""
                        SELECT kd.id,
                               kd.file_object_id,
                               kd.knowledge_base_id,
                               kd.document_title,
                               kd.file_type,
                               kd.parse_status,
                               kd.parse_failure_reason,
                               fo.bucket,
                               fo.object_key,
                               fo.content_type,
                               fo.size_bytes
                        FROM knowledge_documents kd
                        JOIN file_objects fo ON fo.id = kd.file_object_id
                        WHERE kd.id = ?
                        """,
                (rs, rowNum) -> new StoredDocument(
                        rs.getLong("id"),
                        rs.getLong("file_object_id"),
                        rs.getLong("knowledge_base_id"),
                        rs.getString("document_title"),
                        rs.getString("file_type"),
                        rs.getString("parse_status"),
                        rs.getString("bucket"),
                        rs.getString("object_key"),
                        rs.getString("content_type"),
                        rs.getLong("size_bytes"),
                        rs.getString("parse_failure_reason")
                ),
                documentId
        ).stream().findFirst();
    }

    private Long insertFileObject(UploadedDocumentRecord record) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO file_objects(bucket, object_key, file_name, content_type, size_bytes, storage_purpose, owner_user_id)
                    VALUES (?, ?, ?, ?, ?, 'upload', ?)
                    """, new String[]{"id"});
            statement.setString(1, record.bucket());
            statement.setString(2, record.objectKey());
            statement.setString(3, record.fileName());
            statement.setString(4, record.contentType());
            statement.setLong(5, record.sizeBytes());
            statement.setLong(6, record.uploadedBy());
            return statement;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    private Long insertKnowledgeDocument(UploadedDocumentRecord record, Long fileObjectId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO knowledge_documents(
                      knowledge_base_id, file_object_id, document_title, file_type, parse_status,
                      ocr_required, table_recognition_required, uploaded_by
                    )
                    VALUES (?, ?, ?, ?, 'pending', ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, record.knowledgeBaseId());
            statement.setLong(2, fileObjectId);
            statement.setString(3, record.fileName());
            statement.setString(4, record.fileType());
            statement.setBoolean(5, record.ocrRequired());
            statement.setBoolean(6, record.tableRecognitionRequired());
            statement.setLong(7, record.uploadedBy());
            return statement;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }
}
