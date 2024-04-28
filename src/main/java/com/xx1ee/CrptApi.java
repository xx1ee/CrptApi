package com.xx1ee;

import com.google.gson.Gson;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.*;
public class CrptApi {
    private final Gson gson = new Gson();
    private final Semaphore semaphore;
    private final long timeLimitMillis;
    private volatile long lastRequestTime;


    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeLimitMillis = timeUnit.toMillis(1);
        this.semaphore = new Semaphore(requestLimit);
    }

    public String request(Document document, String signature) {
        String response;
        try {
            semaphore.acquire();
            if ((System.currentTimeMillis() - lastRequestTime) < timeLimitMillis) {
                Thread.sleep(timeLimitMillis);
            }
            response = postRequestToApi(document, signature);
            setLastRequestTime(System.currentTimeMillis());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            semaphore.release();
        }
        return response;
    }
    private synchronized void setLastRequestTime(long requestTime) {
        this.lastRequestTime = requestTime;
    }
    private synchronized String postRequestToApi(Document document, String signature) {
        var jsonDocument = gson.toJson(document);
        return HttpSender.sendDocument(jsonDocument, signature);
    }

    static class HttpSender {
        private static final String url = "https://ismp.crpt.ru/api/v3/lk/documents/create";
        private static final HttpClient httpClient;
        private static final HttpPost httpPost;
        static {
            httpClient = HttpClients.createDefault();
            httpPost = new HttpPost(url);
            httpPost.addHeader("content-type", "application/json");
        }
        private HttpSender() {

        }
        public synchronized static String sendDocument(String document, String signature) {
            StringEntity requestBody = null;
            try {
                requestBody = new StringEntity(document);
                httpPost.setEntity(requestBody);
                HttpResponse response = httpClient.execute(httpPost);
                HttpEntity responseEntity = response.getEntity();

                BufferedReader reader = new BufferedReader(new InputStreamReader(responseEntity.getContent()));
                StringBuilder responseJson = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseJson.append(line);
                }
                return responseJson.toString();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
    static class Document
    {
        private Description description;
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private List<Product> products;
        private String reg_date;
        private String reg_number;

        public Document(Description description, String doc_id, String doc_status, String doc_type,
                        boolean importRequest, String owner_inn, String participant_inn, String producer_inn,
                        String production_date, String production_type, List<Product> products, String reg_date,
                        String reg_number) {
            this.description = description;
            this.doc_id = doc_id;
            this.doc_status = doc_status;
            this.doc_type = doc_type;
            this.importRequest = importRequest;
            this.owner_inn = owner_inn;
            this.participant_inn = participant_inn;
            this.producer_inn = producer_inn;
            this.production_date = production_date;
            this.production_type = production_type;
            this.products = products;
            this.reg_date = reg_date;
            this.reg_number = reg_number;
        }

        public static class Description
        {
            private String participantInn;

            public Description(String participantInn) {
                this.participantInn = participantInn;
            }
        }
        public static class Product
        {
            private String certificate_document;
            private String certificate_document_date;
            private String certificate_document_number;
            private String owner_inn;
            private String producer_inn;
            private String production_date;
            private String tnved_code;
            private String uit_code;
            private String uitu_code;

            public Product(String certificate_document, String certificate_document_date, String certificate_document_number,
                           String owner_inn, String producer_inn, String production_date, String tnved_code, String uit_code,
                           String uitu_code) {
                this.certificate_document = certificate_document;
                this.certificate_document_date = certificate_document_date;
                this.certificate_document_number = certificate_document_number;
                this.owner_inn = owner_inn;
                this.producer_inn = producer_inn;
                this.production_date = production_date;
                this.tnved_code = tnved_code;
                this.uit_code = uit_code;
                this.uitu_code = uitu_code;
            }
        }
    }

    public static void main(String[] args) {
        CrptApi.Document document = new Document(new Document.Description("participantinn"), "doc_id", "doc_status",
                "doc_type", true, "owner_inn", "participant_inn",
                "producer_inn", "2020-01-23", "string",
                List.of(new Document.Product("certificate", "2020-01-23",
                        "doc", "owner_inn", "producer_inn", "2020-01-23",
                        "230", "240", "280")),
                "2020-01-23", "string");
        CrptApi crptApi = new CrptApi(TimeUnit.MINUTES, 2);
        for (int i = 0; i < 6; i++) {
            new Thread(() -> {
                System.out.println(crptApi.request(document, "signature"));
            }).start();
        }
    }
}