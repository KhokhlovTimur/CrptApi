import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CrptApi {
    private static ExecutingQueue queueService;
    private final AtomicInteger requestsCount = new AtomicInteger(0);
    private final static ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient client = new OkHttpClient();
    private final int requestLimit;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        assert requestLimit > 0;
        this.requestLimit = requestLimit;
        queueService = new QueueService(timeUnit);
    }

    public static void main(String[] args) {
        CrptApi crptApi = new CrptApi(TimeUnit.MINUTES, 3);
        crptApi.postDocument(createDocument(), "Signature", new DocumentCreateCallback());
    }

    public void postDocument(Document document, String signature, Callback documentCreateCallback) {
        queueService.joinQueue();

        String jsonDocument;
        try {
            jsonDocument = objectMapper.writeValueAsString(document);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }

        sendRequest(jsonDocument, signature, documentCreateCallback);
    }

    private void sendRequest(String jsonDocument, String signature, Callback callback) {
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), jsonDocument);
        Request request = new Request.Builder()
                .url("https://ismp.crpt.ru/api/v3/lk/documents/create")
                .post(body)
                .addHeader("Signature", signature)
                .build();

        Call call = client.newCall(request);
        //асинхронно
        call.enqueue(callback);
    }

    private static class DocumentCreateCallback implements Callback {
        @Override
        public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
            System.out.printf("Response code: %s%n", response.code());
            queueService.releaseOne();
        }

        @Override
        public void onFailure(@NotNull Call call, @NotNull IOException e) {
            System.out.println("Something went wrong");
            queueService.releaseOne();
        }
    }

    private class QueueService implements ExecutingQueue {
        private final Semaphore semaphore = new Semaphore(requestLimit);

        public QueueService(TimeUnit timeUnit) {
            Executors.newScheduledThreadPool(5).scheduleAtFixedRate(this::releaseAll, 1, 1, timeUnit);
        }

        @Override
        public void joinQueue() {
            try {
                semaphore.acquire();

                while (requestsCount.get() >= requestLimit) {}

                requestsCount.incrementAndGet();
            } catch (InterruptedException e) {
                throw new IllegalArgumentException(e);
            }
        }

        @Override
        public void releaseOne() {
            semaphore.release();
        }

        private void releaseAll() {
            requestsCount.set(0);
            System.out.println("Queue is empty now...");
        }
    }

    public interface ExecutingQueue {
        void joinQueue();
        void releaseOne();
    }


    //Models
    @Data
    @AllArgsConstructor
    public static class Document {
        private String participantInn;
        private String docId;

        private String docStatus;
        private String docType;
        private boolean importRequest;
        private String ownerInn;
        private String producerInn;
        private String productionDate;
        private String productionType;
        private List<Product> products;
        private String regDate;
        private String regNumber;

    }

    @Data
    @AllArgsConstructor
    public static class Product {
        private String certificateDocument;
        private String certificateDocumentDate;
        private String certificateDocumentNumber;
        private String ownerInn;
        private String producerInn;
        private String productionDate;
        private String tnvedCode;
        private String uitCode;
        private String uituCode;

    }

    private static Document createDocument() {
        Product product = new Product(
                "certificate_document_value",
                "2020-01-23",
                "certificate_document_number_value",
                "owner_inn_value",
                "producer_inn_value",
                "2020-01-23",
                "tnved_code_value",
                "uit_code_value",
                "uitu_code_value"
        );

        List<Product> products = new ArrayList<>();
        products.add(product);

        return new Document(
                "participantInn_value",
                "doc_id_value",
                "doc_status_value",
                "LP_INTRODUCE_GOODS",
                true,
                "owner_inn_value",
                "producer_inn_value",
                "2020-01-23",
                "production_type_value",
                products,
                "2020-01-23",
                "reg_number_value"
        );
    }
}