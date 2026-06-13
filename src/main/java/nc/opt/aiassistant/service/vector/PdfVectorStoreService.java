package nc.opt.aiassistant.service.vector; //package nc.opt.aiassistant.service.vector;
//
//import java.util.List;
//import nc.opt.aiassistant.service.chat.QueryRewriterService;
//import nc.opt.aiassistant.service.reranker.RerankerService;
//import org.springframework.ai.chat.client.ChatClient;
//import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
//import org.springframework.ai.chat.memory.ChatMemoryRepository;
//import org.springframework.ai.chat.memory.MessageWindowChatMemory;
//import org.springframework.ai.chat.model.ChatModel;
//import org.springframework.ai.document.Document;
//import org.springframework.ai.vectorstore.VectorStore;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.stereotype.Service;
//
//
//@Service
//public class PdfVectorStoreService extends AbstractVectorStoreService {
//
//    public PdfVectorStoreService(
//        @Qualifier("docsVectorStore") VectorStore vectorStore,
//        RerankerService rerankerService,
//        ChatModel chatModel,
//        QueryRewriterService queryRewriterService,
//        ChatMemoryRepository chatMemoryRepository
//    ) {
//        super(
//            vectorStore,
//            rerankerService,
//            ChatClient.builder(chatModel)
//                .defaultAdvisors(
//                    MessageChatMemoryAdvisor.builder(
//                        MessageWindowChatMemory.builder().chatMemoryRepository(chatMemoryRepository).maxMessages(10).build()
//                    ).build()
//                )
//                .build(),
//            queryRewriterService
//        );
//    }
//
//    @Override
//    public DataType getDataType() {
//        return DataType.PDF;
//    }
//
//    @Override
//    public String getContext(String query) {
//        List<Document> docs = fetchDocuments(query, 30);
//
//        // boost abstract
//        docs
//            .stream()
//            .filter(d -> "abstract".equals(d.getMetadata().get("type")))
//            .findFirst()
//            .ifPresent(d -> {
//                docs.remove(d);
//                docs.add(0, d);
//            });
//
//        return rerankAndBuildContext(query, docs, 5);
//    }
//
//    @Override
//    public String getPrompt() {
//        return """
//        You are an AI assistant specialized in PDF document analysis.
//        Use ONLY the provided context to answer the question.
//        If the answer is not explicitly contained in the context, respond with: "I don't know based on the provided information."
//        Be concise and accurate. Cite relevant sources when possible.
//
//        Context:
//        {context}
//
//        Answer in French.
//        """;
//    }
//}
