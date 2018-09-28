package com.cloudbees.jenkins.plugins.amazonecs;

import com.amazonaws.services.ecs.model.*;
import hudson.util.FormValidation;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ECSTaskTemplateTest {

    @Test
    public void whenTaskDefinitionOverrideIsSetThenTemplateNameIsOverridden() {
        ECSTaskTemplate template = new ECSTaskTemplate("Label","TestTemplate","Override","FARGATE");
        assertEquals("Override", template.getTaskDefinitionOverride());
        assertEquals("", template.getTemplateName());
    }

    @Test
    public void whenTaskDefinitionOverrideIsSetToNullThenTemplateNameIsDefaulted() {
        ECSTaskTemplate template = new ECSTaskTemplate("Label","TestTemplate","","FARGATE");
        assertNull(template.getTaskDefinitionOverride());
        assertEquals("TestTemplate", template.getTemplateName());
    }

    @Test
    public void whenSettingLogDriverOptionsWithEmptyListThenTheTemplateIsCleared() {
        ECSTaskTemplate template = new ECSTaskTemplate("Label","TestTemplate",null,"FARGATE").withLogDriverOptions(Arrays.asList(new ECSTaskTemplate.LogDriverOption("Key1", "Value1"), new ECSTaskTemplate.LogDriverOption("Key2", "Value2")));
        Assert.assertEquals(2, template.getLogDriverOptions().size());
        template.setLogDriverOptions(Collections.emptyList());
        Assert.assertEquals(0, template.getLogDriverOptions().size());
    }

    @Test
    public void whenSettingLogDriverOptionsWithNullThenTheTemplateIsCleared() {
        ECSTaskTemplate template = new ECSTaskTemplate("Label","TestTemplate",null,"FARGATE").withLogDriverOptions(Arrays.asList(new ECSTaskTemplate.LogDriverOption("Key1", "Value1"), new ECSTaskTemplate.LogDriverOption("Key2", "Value2")));
        Assert.assertEquals(2, template.getLogDriverOptions().size());
        template.setLogDriverOptions(null);
        Assert.assertEquals(0, template.getLogDriverOptions().size());
    }

    @Test
    public void whenSettingEnvironmentsWithEmptyListThenTheTemplateIsCleared() {
        ECSTaskTemplate template = new ECSTaskTemplate("Label","TestTemplate",null,"FARGATE").withEnvironments(Arrays.asList(new ECSTaskTemplate.EnvironmentEntry("Key1", "Value1"), new ECSTaskTemplate.EnvironmentEntry("Key2", "Value2")));
        Assert.assertEquals(2, template.getEnvironments().size());
        template.setEnvironments(Collections.emptyList());
        Assert.assertEquals(0, template.getEnvironments().size());
    }

    @Test
    public void whenSettingEnvironmentsWithNullThenTheTemplateIsCleared() {
        ECSTaskTemplate template = new ECSTaskTemplate("Label","TestTemplate",null,"FARGATE").withEnvironments(Arrays.asList(new ECSTaskTemplate.EnvironmentEntry("Key1", "Value1"), new ECSTaskTemplate.EnvironmentEntry("Key2", "Value2")));
        Assert.assertEquals(2, template.getEnvironments().size());
        template.setEnvironments(null);
        Assert.assertEquals(0, template.getEnvironments().size());
    }

    @Test
    public void whenSettingExtraHostsWithEmptyListThenTheTemplateIsCleared() {
        ECSTaskTemplate template = new ECSTaskTemplate("Label","TestTemplate",null,"FARGATE").withExtraHosts(Arrays.asList(new ECSTaskTemplate.ExtraHostEntry("Key1", "Value1"), new ECSTaskTemplate.ExtraHostEntry("Key2", "Value2")));
        Assert.assertEquals(2, template.getExtraHosts().size());
        template.setExtraHosts(Collections.emptyList());
        Assert.assertEquals(0, template.getExtraHosts().size());
    }

    @Test
    public void whenSettingExtraHostsWithNullThenTheTemplateIsCleared() {
        ECSTaskTemplate template = new ECSTaskTemplate("Label","TestTemplate",null,"FARGATE").withExtraHosts(Arrays.asList(new ECSTaskTemplate.ExtraHostEntry("Key1", "Value1"), new ECSTaskTemplate.ExtraHostEntry("Key2", "Value2")));
        Assert.assertEquals(2, template.getExtraHosts().size());
        Assert.assertEquals(2, template.getExtraHostEntries().size());
        template.setExtraHosts(null);
        Assert.assertEquals(0, template.getExtraHosts().size());
    }

    @Test
    public void whenSettingMountPointsWithEmptyListThenTheTemplateIsCleared() {
        ECSTaskTemplate template = new ECSTaskTemplate("Label","TestTemplate",null,"FARGATE").withMountPoints(Arrays.asList(new ECSTaskTemplate.MountPointEntry("Name1", "SourcePath1", "ContainerPath1", false), new ECSTaskTemplate.MountPointEntry("Name2", "SourcePath2", "ContainerPath2", false)));
        Assert.assertEquals(2, template.getMountPoints().size());
        template.setMountPoints(Collections.emptyList());
        Assert.assertEquals(0, template.getMountPoints().size());
    }

    @Test
    public void whenSettingMountPointsWithNullThenTheTemplateIsCleared() {
        ECSTaskTemplate template = new ECSTaskTemplate("Label","TestTemplate",null,"FARGATE").withMountPoints(Arrays.asList(new ECSTaskTemplate.MountPointEntry("Name1", "SourcePath1", "ContainerPath1", false), new ECSTaskTemplate.MountPointEntry("Name2", "SourcePath2", "ContainerPath2", false)));
        Assert.assertEquals(2, template.getMountPoints().size());
        template.setMountPoints(null);
        Assert.assertEquals(0, template.getMountPoints().size());
    }

    @Test
    public void whenSettingPortMappingWithEmptyCollectionThenTheTemplateIsCleared() {
        ECSTaskTemplate template = new ECSTaskTemplate("Label","TestTemplate",null,"FARGATE").withPortMappings(Arrays.asList(new ECSTaskTemplate.PortMappingEntry(1000, 1000, "tcp"), new ECSTaskTemplate.PortMappingEntry(1001, 1001, "tcp")));
        Assert.assertEquals(2, template.getPortMappings().size());
        Assert.assertEquals(2, template.getPortMappingEntries().size());
        template.setPortMappings(Collections.emptyList());
        Assert.assertEquals(0, template.getPortMappings().size());
    }

    @Test
    public void whenSettingPortMappingWithNullThenTheTemplateIsCleared() {
        ECSTaskTemplate template = new ECSTaskTemplate("Label","TestTemplate",null,"FARGATE").withPortMappings(Arrays.asList(new ECSTaskTemplate.PortMappingEntry(1000, 1000, "tcp"), new ECSTaskTemplate.PortMappingEntry(1001, 1001, "tcp")));
        Assert.assertEquals(2, template.getPortMappings().size());
        Assert.assertEquals(2, template.getPortMappingEntries().size());
        template.setPortMappings(null);
        Assert.assertEquals(0, template.getPortMappings().size());
    }

    @Test
    public void containerDefinitionIsBuiltCorrectly() {
        ECSTaskTemplate template = new ECSTaskTemplate("Label","TestTemplate",null,"FARGATE")
                .withImage("TestImage")
                .withEnvironments(Arrays.asList(new ECSTaskTemplate.EnvironmentEntry("Key1", "Value1"), new ECSTaskTemplate.EnvironmentEntry("Key2", "Value2")))
                .withExtraHosts(Arrays.asList(new ECSTaskTemplate.ExtraHostEntry("Key1", "Value1"), new ECSTaskTemplate.ExtraHostEntry("Key2", "Value2")))
                .withMountPoints(Arrays.asList(new ECSTaskTemplate.MountPointEntry("Name1", "SourcePath1", "ContainerPath1", false), new ECSTaskTemplate.MountPointEntry("Name2", "SourcePath2", "ContainerPath2", false)))
                .withPortMappings(Arrays.asList(new ECSTaskTemplate.PortMappingEntry(1000, 1000, "tcp"), new ECSTaskTemplate.PortMappingEntry(1001, 1001, "tcp")))
                .withCpu(1)
                .withPrivileged(false)
                .withMemoryReservation(1024)
                .withMemory(1024)
                .withEntrypoint("EntryPoint1 EntryPoint2")
                .withJvmArgs("Args")
                .withDnsSearchDomains("Domain")
                .withContainerUser("User")
                .withLogDriver("awslogs")
                .withLogDriverOptions(Arrays.asList(new ECSTaskTemplate.LogDriverOption("Key1", "Value1"), new ECSTaskTemplate.LogDriverOption("Key2", "Value2")));
        Map<String, String> logOptions = new HashMap<>();
        logOptions.put("Key1", "Value1");
        logOptions.put("Key2", "Value2");
        ContainerDefinition definition = new ContainerDefinition()
                .withEssential(true)
                .withName("ContainerName")
                .withImage("TestImage")
                .withEnvironment(new KeyValuePair().withName("Key1").withValue("Value1"), new KeyValuePair().withName("Key2").withValue("Value2"), new KeyValuePair().withName("JAVA_OPTS").withValue("Args"))
                .withExtraHosts(new HostEntry().withIpAddress("Key1").withHostname("Value1"), new HostEntry().withIpAddress("Key2").withHostname("Value2"))
                .withMountPoints(new MountPoint().withSourceVolume("Name1").withContainerPath("ContainerPath1").withReadOnly(false), new MountPoint().withSourceVolume("Name2").withContainerPath("ContainerPath2").withReadOnly(false))
                .withPortMappings(new PortMapping().withContainerPort(1000).withHostPort(1000).withProtocol("tcp"), new PortMapping().withContainerPort(1001).withHostPort(1001).withProtocol("tcp"))
                .withCpu(1)
                .withPrivileged(false)
                .withMemoryReservation(1024)
                .withMemory(1024)
                .withDnsSearchDomains("Domain")
                .withEntryPoint("EntryPoint1", "EntryPoint2")
                .withUser("User")
                .withLogConfiguration(new LogConfiguration().withLogDriver("awslogs").withOptions(logOptions));
        Assert.assertEquals(definition, template.buildContainerDefinition("ContainerName"));
    }

    @Test
    public void whenTemplateNameAndTaskDefinitionArnAreNotSuppliedThenFormValidationIsError()
    {
        ECSTaskTemplate.DescriptorImpl descriptor=new ECSTaskTemplate.DescriptorImpl();
        FormValidation validation=descriptor.doCheckTemplateName(null,null);
        Assert.assertEquals(FormValidation.Kind.ERROR,validation.kind);
    }

    @Test
    public void whenTemplateNameIsTooLongThenFormValidationIsError()
    {
        ECSTaskTemplate.DescriptorImpl descriptor=new ECSTaskTemplate.DescriptorImpl();
        FormValidation validation=descriptor.doCheckTemplateName(null,StringUtils.repeat("A",128));
        Assert.assertEquals(FormValidation.Kind.ERROR,validation.kind);
    }

    @Test
    public void whenTaskDefinitionArnIsNotValidFormatThenFormValidationIsError()
    {
        ECSTaskTemplate.DescriptorImpl descriptor=new ECSTaskTemplate.DescriptorImpl();
        FormValidation validation=descriptor.doCheckTaskDefinitionOverride("Test",null);
        Assert.assertEquals(FormValidation.Kind.ERROR,validation.kind);
    }

    @Test
    public void whenTaskDefinitionArnIsValidFormatThenFormValidationIsOk()
    {
        ECSTaskTemplate.DescriptorImpl descriptor=new ECSTaskTemplate.DescriptorImpl();
        FormValidation validation=descriptor.doCheckTaskDefinitionOverride("arn:aws:ecs:us-east-1:123456789012:task-definition/hello_world:8",null);
        Assert.assertEquals(FormValidation.Kind.OK,validation.kind);
    }

    @Test
    public void whenTemplateNameIsValidFormatThenFormValidationIsOk()
    {
        ECSTaskTemplate.DescriptorImpl descriptor=new ECSTaskTemplate.DescriptorImpl();
        FormValidation validation=descriptor.doCheckTaskDefinitionOverride(null,"test-template");
        Assert.assertEquals(FormValidation.Kind.OK,validation.kind);
    }

    @Test
    public void whenLaunchTypeIsFargetAndSecGroupNotPopulatedThenFormValidationIsError()
    {
        ECSTaskTemplate.DescriptorImpl descriptor=new ECSTaskTemplate.DescriptorImpl();
        FormValidation validation=descriptor.doCheckSecurityGroups(null,"FARGATE");
        Assert.assertEquals(FormValidation.Kind.ERROR,validation.kind);
    }

    @Test
    public void whenLaunchTypeIsFargetAndSecGroupFormatIsInvalidThenFormValidationIsError()
    {
        ECSTaskTemplate.DescriptorImpl descriptor=new ECSTaskTemplate.DescriptorImpl();
        FormValidation validation=descriptor.doCheckSecurityGroups("sg-1234567890abcdef1,sg-12345","FARGATE");
        Assert.assertEquals(FormValidation.Kind.ERROR,validation.kind);
    }

    @Test
    public void whenLaunchTypeIsFargetAndSecGroupFormatIsValidThenFormValidationIsOK()
    {
        ECSTaskTemplate.DescriptorImpl descriptor=new ECSTaskTemplate.DescriptorImpl();
        FormValidation validation=descriptor.doCheckSecurityGroups("sg-1234567890abcdef1","FARGATE");
        Assert.assertEquals(FormValidation.Kind.OK,validation.kind);
    }

    @Test
    public void whenLaunchTypeIsFargetAndSubnetFormatIsInvalidThenFormValidationIsError()
    {
        ECSTaskTemplate.DescriptorImpl descriptor=new ECSTaskTemplate.DescriptorImpl();
        FormValidation validation=descriptor.doCheckSubnets("subnet-1234567890abcdef1,sg-12345","FARGATE");
        Assert.assertEquals(FormValidation.Kind.ERROR,validation.kind);
    }

    @Test
    public void whenLaunchTypeIsFargetAndSubnetFormatIsValidThenFormValidationIsOK()
    {
        ECSTaskTemplate.DescriptorImpl descriptor=new ECSTaskTemplate.DescriptorImpl();
        FormValidation validation=descriptor.doCheckSubnets("subnet-1234567890abcdef1","FARGATE");
        Assert.assertEquals(FormValidation.Kind.OK,validation.kind);
    }

    @Test
    public void whenNoARNSpecifiedAndTaskIsFargateAndMemoryNotValidThenFormValidationIsError() {
        ECSTaskTemplate.DescriptorImpl descriptor=new ECSTaskTemplate.DescriptorImpl();
        FormValidation validation=descriptor.doCheckMemory(1024,0,null,"FARGATE",1024);
        Assert.assertEquals(FormValidation.Kind.ERROR,validation.kind);
        assertTrue(validation.getMessage().startsWith("For Fargate tasks: The valid Memory settings for CPU"));
    }

    @Test
    public void whenARNSpecifiedAndTaskIsFargateAndMemoryNotValidThenFormValidationIsOk() {
        ECSTaskTemplate.DescriptorImpl descriptor=new ECSTaskTemplate.DescriptorImpl();
        FormValidation validation=descriptor.doCheckMemoryReservation(1024,0,"TestARN","FARGATE",1024);
        Assert.assertEquals(FormValidation.Kind.OK,validation.kind);
    }

    @Test
    public void whenNoARNSpecifiedAndTaskIsFargateAndNeitherMemoryOrMemoryReservationAreSetThenFormValidationIsError() {
        ECSTaskTemplate.DescriptorImpl descriptor=new ECSTaskTemplate.DescriptorImpl();
        FormValidation validation=descriptor.doCheckMemoryReservation(0,0,null,"FARGATE",1024);
        Assert.assertEquals(FormValidation.Kind.ERROR,validation.kind);
        assertTrue(validation.getMessage().equals("at least one of memory or memoryReservation are required to be &gt; 0"));
    }

    @Test
    public void whenNoARNSpecifiedAndTaskIsFargateAndMemoryIsValidThenFormValidationIsOk() {
        ECSTaskTemplate.DescriptorImpl descriptor=new ECSTaskTemplate.DescriptorImpl();
        FormValidation validation=descriptor.doCheckMemory(1024,0,null,"FARGATE",512);
        Assert.assertEquals(FormValidation.Kind.OK,validation.kind);
    }

    @Test
    public void whenNoARNSpecifiedAndTaskIsNotFargateAndMemoryIsValidThenFormValidationIsOk() {
        ECSTaskTemplate.DescriptorImpl descriptor=new ECSTaskTemplate.DescriptorImpl();
        FormValidation validation=descriptor.doCheckMemory(1024,0,null,"EC2",1024);
        Assert.assertEquals(FormValidation.Kind.OK,validation.kind);
    }

    @Test
    public void whenNoARNSpecifiedAndTaskIsNotFargateAndMemoryIsLessThanZeroThenFormValidationIsError() {
        ECSTaskTemplate.DescriptorImpl descriptor=new ECSTaskTemplate.DescriptorImpl();
        FormValidation validation=descriptor.doCheckMemory(-100,0,null,"EC2",1024);
        Assert.assertEquals(FormValidation.Kind.ERROR,validation.kind);
    }

    @Test
    public void whenNoARNSpecifiedAndTaskIsNotFargateAndMemoryIsLessThanMemoryReservationThenFormValidationIsError() {
        ECSTaskTemplate.DescriptorImpl descriptor=new ECSTaskTemplate.DescriptorImpl();
        FormValidation validation=descriptor.doCheckMemory(1024,2048,null,"EC2",1024);
        Assert.assertEquals(FormValidation.Kind.ERROR,validation.kind);
    }

    @Test
    public void whenNoARNSpecifiedAndTaskIsFargateAndCpuIsNotValidThenFormValidationIsError() {
        ECSTaskTemplate.DescriptorImpl descriptor=new ECSTaskTemplate.DescriptorImpl();
        FormValidation validation=descriptor.doCheckCpu(null,"FARGATE",128);
        Assert.assertEquals(FormValidation.Kind.ERROR,validation.kind);
    }

    @Test
    public void whenNoARNSpecifiedAndTaskIsNotFargateAndCpuIsNotValidThenFormValidationIsError() {
        ECSTaskTemplate.DescriptorImpl descriptor=new ECSTaskTemplate.DescriptorImpl();
        FormValidation validation=descriptor.doCheckCpu(null,"EC2",64);
        Assert.assertEquals(FormValidation.Kind.ERROR,validation.kind);
    }

    @Test
    public void whenNoARNSpecifiedAndTaskIsNotFargateAndCpuIsHigherThanAllowedThenFormValidationIsError() {
        ECSTaskTemplate.DescriptorImpl descriptor=new ECSTaskTemplate.DescriptorImpl();
        FormValidation validation=descriptor.doCheckCpu(null,"EC2",10250);
        Assert.assertEquals(FormValidation.Kind.ERROR,validation.kind);
    }

    @Test
    public void whenNoARNSpecifiedAndTaskIsNotFargateAndCpuIsValidThenFormValidationIsOK() {
        ECSTaskTemplate.DescriptorImpl descriptor=new ECSTaskTemplate.DescriptorImpl();
        FormValidation validation=descriptor.doCheckCpu(null,"EC2",1024);
        Assert.assertEquals(FormValidation.Kind.OK,validation.kind);
    }

    @Test
    public void whenNoARNSpecifiedAndTaskIsFargateAndCpuIsValidThenFormValidationIsOK() {
        ECSTaskTemplate.DescriptorImpl descriptor=new ECSTaskTemplate.DescriptorImpl();
        FormValidation validation=descriptor.doCheckCpu(null,"FARGATE",1024);
        Assert.assertEquals(FormValidation.Kind.OK,validation.kind);
    }
}
