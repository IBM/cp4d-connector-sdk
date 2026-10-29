/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector.integ;

/**
 * Entry point for the {@code generateIntegProperties} Gradle task.
 *
 * <p>Accepts one or more DSL JSON paths as arguments and generates a ready-to-fill
 * {@code .properties} file beside each one that does not already have one.
 *
 * <p>Not intended for direct use — invoke via Gradle:
 * <pre>
 *   ./gradlew :wdp-connect-sdk-gen-java-connectors_forge_rest-impl:generateIntegProperties \
 *       -Pdsls=path/to/braintree-api.json,path/to/servicenow.json
 * </pre>
 */
public final class GeneratePropertiesMain {

    private GeneratePropertiesMain() { }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: GeneratePropertiesMain <dsl.json> [<dsl.json> ...]");
            System.exit(1);
        }

        int generated = 0;
        int skipped   = 0;

        for (final String arg : args) {
            final String result = PropertiesFileGenerator.generate(arg.strip());
            if (result != null) {
                System.out.println("GENERATED: " + result);
                generated++;
            } else {
                System.out.println("SKIP: " + arg.strip());
                skipped++;
            }
        }

        System.out.println();
        System.out.printf("Done — %d generated, %d skipped.%n", generated, skipped);
    }
}
