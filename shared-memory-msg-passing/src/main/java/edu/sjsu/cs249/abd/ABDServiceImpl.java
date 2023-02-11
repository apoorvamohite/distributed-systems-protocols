package edu.sjsu.cs249.abd;

import java.util.HashMap;

import edu.sjsu.cs249.abd.ABDServiceGrpc.ABDServiceImplBase;
import edu.sjsu.cs249.abd.Grpc.EnableRequest;
import edu.sjsu.cs249.abd.Grpc.EnableResponse;
import edu.sjsu.cs249.abd.Grpc.ExitRequest;
import edu.sjsu.cs249.abd.Grpc.ExitResponse;
import edu.sjsu.cs249.abd.Grpc.NameRequest;
import edu.sjsu.cs249.abd.Grpc.NameResponse;
import edu.sjsu.cs249.abd.Grpc.Read1Request;
import edu.sjsu.cs249.abd.Grpc.Read1Response;
import edu.sjsu.cs249.abd.Grpc.Read2Request;
import edu.sjsu.cs249.abd.Grpc.Read2Response;
import edu.sjsu.cs249.abd.Grpc.WriteRequest;
import edu.sjsu.cs249.abd.Grpc.WriteResponse;
import io.grpc.stub.StreamObserver;

public class ABDServiceImpl extends ABDServiceImplBase {
    private Boolean read1Enabled = true;
    private Boolean read2Enabled = true;
    private Boolean writeEnabled = true;
    private HashMap<Long, RegisterEntry> register;

    public ABDServiceImpl() {
        super();
        this.register = new HashMap<Long, RegisterEntry>();
    }

    @Override
    public void enableRequests(EnableRequest request, StreamObserver<EnableResponse> responseObserver) {
        read1Enabled = request.getRead1();
        read2Enabled = request.getRead2();
        writeEnabled = request.getWrite();
        EnableResponse response = EnableResponse.newBuilder()
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
        System.out.println(
                "Enable Status: \nread1: " + read1Enabled + "\nread2: " + read2Enabled + "\nwrite: " + writeEnabled);
    }

    @Override
    public void exit(ExitRequest request, StreamObserver<ExitResponse> responseObserver) {
        ExitResponse response = ExitResponse.newBuilder()
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
        System.out.println("Shutting down");
        System.exit(0);
    }

    @Override
    public void name(NameRequest request, StreamObserver<NameResponse> responseObserver) {
        NameResponse response = NameResponse.newBuilder()
                .setName("Apoorva")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void read1(Read1Request request, StreamObserver<Read1Response> responseObserver) {
        if (read1Enabled) {
            Long addr = request.getAddr();
            Grpc.Read1Response.Builder rb = Read1Response.newBuilder();
            if (register.containsKey(addr)) {
                rb.setRc(0).setValue(register.get(addr).value).setLabel(register.get(addr).timestamp);
            } else {
                rb.setRc(1);
            }
            Read1Response response = rb.build();
            System.out.println("Read1 Responding "+ addr+" "+ register.get(addr));

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void read2(Read2Request request, StreamObserver<Read2Response> responseObserver) {
        if (read2Enabled) {
            synchronized (register) {
                if (!register.containsKey(request.getAddr())
                        || register.get(request.getAddr()).timestamp < request.getLabel()) {
                    register.put(request.getAddr(), new RegisterEntry(request.getLabel(), request.getValue()));
                    System.out.println("Read2 == Wrote " + register.get(request.getAddr()) + " at " + request.getAddr());
                }
            }
            Read2Response response = Read2Response.newBuilder()
                    .build();
            System.out.println("Read2 Responding "+ request.getAddr() + " " + register.get(request.getAddr()));

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void write(WriteRequest request, StreamObserver<WriteResponse> responseObserver) {
        if (writeEnabled) {
            synchronized (register) {
                if (!register.containsKey(request.getAddr())
                        || register.get(request.getAddr()).timestamp < request.getLabel()) {
                    register.put(request.getAddr(), new RegisterEntry(request.getLabel(), request.getValue()));
                }
            }
            System.out.println("Wrote " + register.get(request.getAddr()) + " at " + request.getAddr());
            WriteResponse response = WriteResponse.newBuilder()
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    public class RegisterEntry {
        Long timestamp;
        Long value;

        public RegisterEntry(Long label, Long value) {
            this.timestamp = label;
            this.value = value;
        }

        @Override
        public String toString() {
            return "RegisterEntry [timestamp=" + timestamp + ", value=" + value + "]";
        }
    }
}