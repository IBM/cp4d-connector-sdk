/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.validator;

import static com.ibm.connect.sdk.rest.RestMsgs.INVALID_YAML_CONFIG;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

import com.ibm.connect.sdk.test.helper.RestTestUtils;

public class RestYamlValidatorTest {

    @Test
    public void testValidateSchema() {
        final List<String> validYamlFiles = Arrays.asList("com/ibm/connect/sdk/rest/validator/valid_sample_rest_configuration.yml",
                "com/ibm/connect/sdk/rest/validator/valid_nested_rest_configuration.yml",
                "com/ibm/connect/sdk/rest/validator/valid_pagination_offset.yml");
        validYamlFiles.forEach(yamlFilePath -> {
            try {
                final String restConfigYaml = RestTestUtils.readResourceFile(yamlFilePath);
                RestYamlValidator.validateSchema(restConfigYaml);
            } catch (Exception e){
                fail("Unable to validate rest configuration yaml file " + yamlFilePath + ". Reason: " + e.getMessage());
            }
        });
    }

    @Test
    public void testValidateSchemaWithMandatoryAndAdditionalProperties() {
        final String yamlStream = "com/ibm/connect/sdk/rest/validator/invalid_missing_required_rest_configuration.yml";
        try {
            final String restConfigYaml = RestTestUtils.readResourceFile(yamlStream);
            RestYamlValidator.validateSchema(restConfigYaml);
            fail("Expected to fail due to validation error");
        } catch (Exception e) {
            final List<String> missingPropsPath = List.of("name", "endpoint", "method", "uniqueIdField", "labelField");
            final List<String> extraProps = List.of("name1", "fieldsIncorrect");
            final String basePath = "$.entityTypes[0]";
            final String errorMessage = missingPropsPath.stream().map(prop ->
                    basePath + ": required property '" + prop + "' not found; "
            ).collect(Collectors.joining()) +
                    extraProps.stream().map(prop ->
                            basePath + ": property '" + prop
                                    + "' is not defined in the schema and the schema does not allow additional properties"
                    ).collect(Collectors.joining("; "));
            Assert.assertEquals(INVALID_YAML_CONFIG.format(errorMessage), e.getMessage());
        }
    }

    @Test
    public void testValidateSchemaHttpMethod() {
        try {
            final String restConfigYaml = RestTestUtils.readResourceFile("com/ibm/connect/sdk/rest/validator/invalid_http_method_rest_configuration.yml");
            RestYamlValidator.validateSchema(restConfigYaml);
        } catch (Exception e){
            Assert.assertEquals(INVALID_YAML_CONFIG.format("$.entityTypes[0].method: does not have a value in the enumeration [\"GET\", \"POST\", \"PUT\", \"PATCH\", \"DELETE\"]"), e.getMessage());
        }
    }

    @Test
    public void testValidateSchemaPaginationType() {
        try {
            final String restConfigYaml = RestTestUtils.readResourceFile("com/ibm/connect/sdk/rest/validator/invalid_pagination_rest_configuration.yml");
            RestYamlValidator.validateSchema(restConfigYaml);
        } catch (Exception e){
            Assert.assertEquals(INVALID_YAML_CONFIG.format("$.entityTypes[0].pagination.type: does not have a value in the enumeration [\"offset\", \"page\", \"link_header\"]"), e.getMessage());
        }
    }

    @Test
    public void testValidateSchemaOffsetPaginationMissingParams() {
        try {
            final String restConfigYaml = RestTestUtils.readResourceFile("com/ibm/connect/sdk/rest/validator/invalid_pagination_offset_missing_params.yml");
            RestYamlValidator.validateSchema(restConfigYaml);
        } catch (Exception e){
            final String errorMessage = "$.entityTypes[0].pagination: required property 'offsetParam' not found";
            Assert.assertTrue("Error message should contain: " + errorMessage + ", but was: " + e.getMessage(),
                    e.getMessage().contains(errorMessage));
        }
    }

    @Test
    public void testValidateSchemaPagePaginationMissingParams() {
        try {
            final String restConfigYaml = RestTestUtils.readResourceFile("com/ibm/connect/sdk/rest/validator/invalid_pagination_page_missing_params.yml");
            RestYamlValidator.validateSchema(restConfigYaml);
        } catch (Exception e){
            final String errorMessage = "$.entityTypes[0].pagination: required property 'sizeParam' not found";
            Assert.assertTrue("Error message should contain: " + errorMessage + ", but was: " + e.getMessage(),
                    e.getMessage().contains(errorMessage));
        }
    }
}
