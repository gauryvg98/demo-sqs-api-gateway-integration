package com.example.test.demo;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.apigateway.model.CreateRestApiRequest;
import software.amazon.awssdk.services.apigateway.model.CreateRestApiResponse;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.AddPermissionRequest;
import java.util.HashMap;
import java.util.Map;

public class App {
    private static final String AWS_ACCESS_KEY = "YOUR_ACCESS_KEY";
    private static final String AWS_SECRET_KEY = "YOUR_SECRET_KEY";
    private static final Region REGION = Region.US_EAST_1;

    private final SqsClient sqsClient;
    private final ApiGatewayClient apiGatewayClient;
    private final LambdaClient lambdaClient;

    public App() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(AWS_ACCESS_KEY, AWS_SECRET_KEY);
        this.sqsClient = SqsClient.builder()
                                  .credentialsProvider(StaticCredentialsProvider.create(credentials))
                                  .region(REGION)
                                  .build();
        this.apiGatewayClient = ApiGatewayClient.builder()
                                                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                                                .region(REGION)
                                                .build();
        this.lambdaClient = LambdaClient.builder()
                                        .credentialsProvider(StaticCredentialsProvider.create(credentials))
                                        .region(REGION)
                                        .build();
    }

    public String createSQSQueue(String queueName, String lambdaArn) {
        Map<QueueAttributeName, String> attributes = new HashMap<>();
        attributes.put(QueueAttributeName.FIFO_QUEUE, "true");
        CreateQueueRequest createQueueRequest = CreateQueueRequest.builder()
                                                                 .queueName(queueName + ".fifo")
                                                                 .attributes(attributes)
                                                                 .build();
        String queueUrl = sqsClient.createQueue(createQueueRequest).queueUrl();

        // Permission for Lambda to poll the SQS queue
        //String policy = "{\"Version\":\"2012-10-17\",\"Id\":\"" + queueUrl + "/SQSDefaultPolicy\",\"Statement\":[{\"Sid\":\"AllowLambdaInvoke\",\"Effect\":\"Allow\",\"Principal\":{\"Service\":\"lambda.amazonaws.com\"},\"Action\":\"SQS:SendMessage\",\"Resource\":\"" + queueUrl + "\",\"Condition\":{\"ArnEquals\":{\"aws:SourceArn\":\"" + lambdaArn + "\"}}}]}";
        sqsClient.tagQueue(builder -> builder.queueUrl(queueUrl).tags(Map.of("lambdaTrigger", "enabled")));

        //adding permission requests to lambda for invoking from a SQS queue
        AddPermissionRequest addPermissionRequest = AddPermissionRequest.builder()
                                                                        .functionName(lambdaArn)
                                                                        .statementId("AllowExecutionFromSQS")
                                                                        .action("lambda:InvokeFunction")
                                                                        .principal("sqs.amazonaws.com")
                                                                        .sourceArn(queueUrl)
                                                                        .build();
        lambdaClient.addPermission(addPermissionRequest);

        return queueUrl;
    }

    public String createApiGateway(String apiName) {
        CreateRestApiRequest createRestApiRequest = CreateRestApiRequest.builder()
                                                                       .name(apiName)
                                                                       .build();
        CreateRestApiResponse response = apiGatewayClient.createRestApi(createRestApiRequest);
        return response.id();
    }

    public void attachSQSToApiGateway(String apiId, String queueName) {
        String integrationUri = String.format("arn:aws:apigateway:%s:sqs:path/%s/%s", REGION.id(), "ACCOUNT_ID", queueName);
        apiGatewayClient.putIntegration(builder -> builder.restApiId(apiId)
                                                             .httpMethod("POST")
                                                             .type("AWS")
                                                             .integrationHttpMethod("POST")
                                                             .uri(integrationUri));
        System.out.println("API Gateway Integrated with SQS at: " + integrationUri);
    }

    public void setupInfrastructure(String queueName, String apiName, String lambdaArn) {
        String queueUrl = createSQSQueue(queueName, lambdaArn);
        String apiId = createApiGateway(apiName);
        attachSQSToApiGateway(apiId, queueUrl);

        System.out.println("Setup completed. API Gateway ID: " + apiId);
    }

    public static void main(String[] args) {
        App app = new App();

        //pass in the details here

        app.setupInfrastructure("FIFO_SQS_QUEUE_NAME", "API_GATEWAY_NAME", "LAMBDA_ARN");
    }
}