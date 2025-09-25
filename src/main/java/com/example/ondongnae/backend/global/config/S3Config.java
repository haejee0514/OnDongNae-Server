package com.example.ondongnae.backend.global.config;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class S3Config {

    @Value("${AWS_ACCESS_KEY_ID}")
    private String AWS_ACCESS_KEY;

    @Value("${AWS_SECRET_ACCESS_KEY}")
    private String AWS_SECRET_KEY;

    @Value("${AWS_REGION}")
    private String AWS_REGION;

    @Bean
    public AmazonS3 amazonS3() {
        return AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(
                        new BasicAWSCredentials(AWS_ACCESS_KEY, AWS_SECRET_KEY)))
                .withRegion(AWS_REGION).build();
    }
}
