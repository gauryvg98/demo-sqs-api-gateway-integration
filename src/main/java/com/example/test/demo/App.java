package com.example.test.demo;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.apigateway.AmazonApiGateway;
import com.amazonaws.services.apigateway.AmazonApiGatewayClientBuilder;
import com.amazonaws.services.apigateway.model.*;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.*;

import java.util.List;

public class App {

    private static final String AWS_ACCESS_KEY = "YOUR_ACCESS_KEY";
    private static final String AWS_SECRET_KEY = "YOUR_SECRET_KEY";
    private static final Regions REGION = Regions.US_EAST_1;

    private final AmazonSQS sqsClient;
    private final AmazonApiGateway apiGatewayClient;

    public App() {
        BasicAWSCredentials credentials = new BasicAWSCredentials(AWS_ACCESS_KEY, AWS_SECRET_KEY);
        this.sqsClient = AmazonSQSClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(REGION)
                .build();
        this.apiGatewayClient = AmazonApiGatewayClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(REGION)
                .build();
    }

    public String createSQSQueue(String queueName) {
        CreateQueueRequest createQueueRequest = new CreateQueueRequest(queueName + ".fifo")
                .addAttributesEntry(QueueAttributeName.FifoQueue.toString(), "true");
        CreateQueueResult createQueueResult = sqsClient.createQueue(createQueueRequest);
        return createQueueResult.getQueueUrl();
    }

    public String createApiGateway(String apiName) {
        CreateRestApiRequest createRestApiRequest = new CreateRestApiRequest()
                .withName(apiName);
        CreateRestApiResult createRestApiResponse = apiGatewayClient.createRestApi(createRestApiRequest);
        return createRestApiResponse.getId();
    }

    public void attachSQSToApiGateway(String apiId, String queueUrl) {
        PutIntegrationRequest putIntegrationRequest = new PutIntegrationRequest()
                .withRestApiId(apiId)
                .withResourceId("root")
                .withHttpMethod("POST")
                .withType(IntegrationType.AWS)
                .withIntegrationHttpMethod("POST")
                .withUri("arn:aws:apigateway:" + REGION.getName() + ":sqs:path/" + queueUrl);
        apiGatewayClient.putIntegration(putIntegrationRequest);

        apiGatewayClient.putMethodResponse(new PutMethodResponseRequest()
                .withRestApiId(apiId)
                .withResourceId("root")
                .withHttpMethod("POST")
                .withStatusCode("200"));

        apiGatewayClient.putIntegrationResponse(new PutIntegrationResponseRequest()
                .withRestApiId(apiId)
                .withResourceId("root")
                .withHttpMethod("POST")
                .withStatusCode("200")
                .withResponseParameters(null));
    }

    public void scriptRun(String queueName, String apiGatewayName) {
        // Create SQS Queue
        String queueUrl = this.createSQSQueue(queueName);

        // Add relevant access policy to the queue
        this.addSQSQueuePolicy(queueUrl);

        // Generate API Gateway with given name
        String apiId = this.createApiGateway(apiGatewayName);

        // Attach SQS to the API Gateway
        this.attachSQSToApiGateway(apiId, queueUrl);

        // Print relevant URL
        System.out.println("API Gateway URL for " + apiGatewayName + ":");
        System.out.println("https://" + apiId + ".execute-api." + REGION.getName() + ".amazonaws.com/default");
        System.out.println();
    }

    public void addSQSQueuePolicy(String queueUrl) {
        String queueArn = sqsClient.getQueueAttributes(queueUrl, List.of("QueueArn")).getAttributes().get("QueueArn");

        String policy = "{\n" +
                "  \"Version\": \"2012-10-17\",\n" +
                "  \"Statement\": [{\n" +
                "    \"Effect\": \"Allow\",\n" +
                "    \"Principal\": \"*\",\n" +
                "    \"Action\": \"SQS:SendMessage\",\n" +
                "    \"Resource\": \"" + queueArn + "\",\n" +
                "    \"Condition\": {\n" +
                "      \"ArnEquals\": {\n" +
                "        \"aws:SourceArn\": \"arn:aws:execute-api:" + REGION.getName() + ":*:*\"\n" +
                "      }\n" +
                "    }\n" +
                "  }]\n" +
                "}";

        sqsClient.setQueueAttributes(new SetQueueAttributesRequest()
                .withQueueUrl(queueUrl)
                .addAttributesEntry(QueueAttributeName.Policy.toString(), policy));
    }

    public static void main(String[] args) {
        App manager = new App();

        // Input list of queue names and API Gateway names
        List<String> queueApiPairs = List.of("FIFO_SQS_QUEUE_NAME1", "API_GATEWAY_NAME1",
                                              "FIFO_SQS_QUEUE_NAME2", "API_GATEWAY_NAME2");

        // Process each pair
        for (int i = 0; i < queueApiPairs.size(); i += 2) {
            String queueName = queueApiPairs.get(i);
            String apiName = queueApiPairs.get(i + 1);
                
            manager.scriptRun(queueName, apiName);
        }
    }
}