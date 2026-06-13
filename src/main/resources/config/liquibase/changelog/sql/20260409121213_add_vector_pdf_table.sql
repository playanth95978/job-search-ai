CREATE TABLE IF NOT EXISTS vector_pdf
(
  id         uuid                     DEFAULT uuid_generate_v4() PRIMARY KEY,
  session_id   VARCHAR(100),
  document_id  VARCHAR(100),
  content    text,
  metadata   jsonb,
  embedding  vector(768),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()

);

CREATE INDEX IF NOT EXISTS vector_pdf_embedding_idx
  ON vector_pdf
    USING HNSW (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS vector_pdf_session_idx
  ON vector_pdf(session_id);

CREATE INDEX IF NOT EXISTS vector_pdf_doc_idx
  ON vector_pdf(document_id);

CREATE INDEX vector_pdf_metadata_idx
  ON vector_pdf USING GIN (metadata);

ANALYZE vector_pdf;
