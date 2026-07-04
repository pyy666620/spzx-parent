package com.spzx.file.test;

import io.minio.*;
import io.minio.errors.MinioException;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class FileUploader {
    public static void main(String[] args)
            throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        try {
            //创建一个minio对象
            MinioClient minioClient =
                    MinioClient.builder()
                            //设置minio地址
                            .endpoint("http://192.168.6.100:9000")
                            .credentials("admin", "admin123456")
                            .build();

            // 判断某个桶是否存在
            boolean found =
                    minioClient.bucketExists(BucketExistsArgs.builder().bucket("spzx").build());
            if (!found) {
                // 如果桶不存在的话则创建它
                minioClient.makeBucket(MakeBucketArgs.builder().bucket("spzx").build());
            } else {
                System.out.println("Bucket 'spzx' already exists.");
            }

            //创建uploadObjectArgs对象
            UploadObjectArgs uploadObjectArgs = UploadObjectArgs.builder()
                    // 设置桶的名字
                    .bucket("spzx")
            // 设置上传到minio服务器之后文件的名字
                    .object("wuqian.jpg")
            // 设置要上传的文件的路径
                     .filename("E:/凡凡.jpg")
                    .build();
            //设置客户端要上传的本地路径
            minioClient.uploadObject(uploadObjectArgs);
//            PutObjectArgs putObjectArgs = PutObjectArgs.builder(""    )
//                    .object("")
//                    .stream()
//                    .build();
            System.out.println("文件上传成功");
        } catch (MinioException e) {
            System.out.println("Error occurred: " + e);
            System.out.println("HTTP trace: " + e.httpTrace());
        }
    }
}