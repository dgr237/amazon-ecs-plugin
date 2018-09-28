package com.cloudbees.jenkins.plugins.amazonecs;

import com.amazonaws.services.ecs.model.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import java.util.List;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class ECSServiceTest {
    private ECSService service;
    private ECSClient mockClient;

    @Before
    public void setup() {
        service=new ECSService("TestCredentials","us-east-1");
        mockClient=mock(ECSClient.class);
        service.init(mockClient);
    }

    @Test
    public void getClusterArns()  {
        doAnswer((Answer<ListClustersResult>) invocationOnMock -> {
            ListClustersRequest request=invocationOnMock.getArgumentAt(0,ListClustersRequest.class);
            if(request.getNextToken()==null)
            {
                return new ListClustersResult().withNextToken("NextToken").withClusterArns("Cluster1","Cluster2");
            }
            else {
                return new ListClustersResult().withClusterArns("Cluster3");
            }
        }).when(mockClient).listClusters(any());
        List<String> clusterArns=service.getClusterArns();
        Assert.assertArrayEquals(new String[] {"Cluster1","Cluster2","Cluster3"},clusterArns.toArray());
    }

    @Test
    public void getRunningTasks() {
        doAnswer((Answer<ListTasksResult>) invocationOnMock -> {
            ListTasksRequest request=invocationOnMock.getArgumentAt(0,ListTasksRequest.class);
            if(request.getNextToken()==null)
            {
                return new ListTasksResult().withNextToken("NextToken").withTaskArns("Task1","Task2");
            }
            else {
                return new ListTasksResult().withTaskArns("Task3");
            }
        }).when(mockClient).listTasks(any());
        List<String> taskArns=service.getRunningTasks("Cluster1");
        Assert.assertArrayEquals(new String[] {"Task1","Task2","Task3"},taskArns.toArray());
    }




    @Test
    public void areSufficientClusterResourcesAvailable() {
        String clusterArn = "Cluster1";
        doAnswer((Answer<ListContainerInstancesResult>) invocationOnMock -> {
            ListContainerInstancesRequest request = invocationOnMock.getArgumentAt(0, ListContainerInstancesRequest.class);
            if (request.getNextToken() == null) {
                return new ListContainerInstancesResult().withNextToken("NextToken").withContainerInstanceArns("Container1", "Container2");
            } else {
                return new ListContainerInstancesResult().withContainerInstanceArns("Container3");
            }
        }).when(mockClient).listContainerInstances(any());
        doAnswer((Answer<DescribeContainerInstancesResult>) invocationOnMock -> new DescribeContainerInstancesResult().withContainerInstances(
                new ContainerInstance().withContainerInstanceArn("Container1").withRemainingResources(new Resource().withName("MEMORY").withIntegerValue(1024), new Resource().withName("CPU").withIntegerValue(1024)),
                new ContainerInstance().withContainerInstanceArn("Container2").withRemainingResources(new Resource().withName("MEMORY").withIntegerValue(512), new Resource().withName("CPU").withIntegerValue(512)),
                new ContainerInstance().withContainerInstanceArn("Container3").withRemainingResources(new Resource().withName("MEMORY").withIntegerValue(4096), new Resource().withName("CPU").withIntegerValue(4096)))).when(mockClient).describeContainerInstances(any());

        ECSTaskTemplate testTemplate=new ECSTaskTemplate("maven-java","maven-java",null,"FARGATE")
                .withImage("cloudbees/maven-java")
                .withMemory(2048)
                .withCpu(2048)
                .withAssignPublicIp(true)
                .withSecurityGroups("secGroup")
                .withSubnets("subnets")
                .withPrivileged(true)
                .withSingleRunTask(true)
                .withSlaveLaunchTimeoutSeconds(2)
                .withIdleTerminationMinutes(1);
        boolean result = service.areSufficientClusterResourcesAvailable(testTemplate, clusterArn);
        Assert.assertTrue(result);
    }
}
