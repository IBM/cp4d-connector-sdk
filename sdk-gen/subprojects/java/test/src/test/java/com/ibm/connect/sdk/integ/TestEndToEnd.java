package com.ibm.connect.sdk.integ;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;

import org.apache.arrow.flight.Action;
import org.apache.arrow.flight.ActionType;
import org.apache.arrow.flight.FlightClient;
import org.apache.arrow.flight.Location;
import org.apache.arrow.flight.Result;
import org.apache.arrow.memory.RootAllocator;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ibm.connect.sdk.util.AuthUtils;
import com.ibm.connect.sdk.util.ClientTokenAuthHandler;
import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightActionResponse;

public class TestEndToEnd
{

    private static FlightClient client;
    private static ModelMapper modelMapper = new ModelMapper();

    @BeforeClass
    public static void setUpOnce() throws Exception
    {
        java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        TimeZone.setDefault(TimeZone.getTimeZone("GMT"));
        final ClassLoader classLoader = TestEndToEnd.class.getClassLoader();
        final File file = new File(classLoader.getResource("localhost.crt").getFile());
        // TODO once deployment is ready location should be retrieved from its output
        client = FlightClient.builder(new RootAllocator(Long.MAX_VALUE), Location.forGrpcInsecure("localhost", 443)).useTls()
                .trustedCertificates(Files.newInputStream(Paths.get(file.toURI()))).build();
        final File prv = new File(classLoader.getResource("private_unencrypted.pem").getFile());
        final File pub = new File(classLoader.getResource("public.pem").getFile());
        client.authenticate(new ClientTokenAuthHandler(AuthUtils.getAuthToken(readPublicKey(pub), readPrivateKey(prv))));
    }

    @Test
    public void testGetDatasourceTypes() throws Exception
    {
        final Iterator<Result> result = client.doAction(new Action("list_datasource_types"));
        final CustomFlightActionResponse actionResponse = modelMapper.fromBytes(result.next().getBody(), CustomFlightActionResponse.class);
        assertEquals(1, actionResponse.getDatasourceTypes().getDatasourceTypes().size());
        assertEquals("integtest", actionResponse.getDatasourceTypes().getDatasourceTypes().get(0).getName());
    }

    @Test
    public void testListActions() throws Exception
    {
        final Iterable<ActionType> actionTypes = client.listActions();
        final List<ActionType> actions = new LinkedList<>();
        actionTypes.forEach(actions::add);
        assertNotNull(actions);
        assertEquals(7, actions.size());
    }

    public static RSAPublicKey readPublicKey(File file) throws Exception
    {
        final String key = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        final KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        try (PemReader pemReader = new PemReader(new StringReader(key))) {
            final PemObject pemObject = pemReader.readPemObject();
            final byte[] decodedKey = pemObject.getContent();
            return (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(decodedKey));
        }
    }

    public static RSAPrivateKey readPrivateKey(File file) throws Exception
    {
        final String key = new String(Files.readAllBytes(file.toPath()), Charset.defaultCharset());
        final KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        try (PemReader pemReader = new PemReader(new StringReader(key))) {
            final PemObject pemObject = pemReader.readPemObject();
            final byte[] decodedKey = pemObject.getContent();
            return (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decodedKey));
        }
    }
}
