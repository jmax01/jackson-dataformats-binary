package com.fasterxml.jackson.dataformat.smile.mapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.SequenceInputStream;
import java.util.*;

import org.junit.Assert;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

public class SmileMapperTest extends BaseTestForSmile
{
    static class BytesBean {
        public byte[] bytes;
        
        public BytesBean() { }
        public BytesBean(byte[] b) { bytes = b; }
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */
    
    private final ObjectMapper MAPPER = smileMapper();

    public void testBinary() throws IOException
    {
        byte[] input = new byte[] { 1, 2, 3, -1, 8, 0, 42 };
        byte[] smile = MAPPER.writeValueAsBytes(new BytesBean(input));
        BytesBean result = MAPPER.readValue(smile, BytesBean.class);
        
        assertNotNull(result.bytes);
        Assert.assertArrayEquals(input, result.bytes);
    }

    // UUIDs should be written as binary (starting with 2.3)
    public void testUUIDs() throws IOException
    {
        UUID uuid = UUID.randomUUID();
        byte[] bytes = MAPPER.writeValueAsBytes(uuid);

        // first, just verify we can decode it
        UUID out = MAPPER.readValue(bytes, UUID.class);
        assertEquals(uuid, out);

        // then verify it comes back as binary
        JsonParser p = MAPPER.getFactory().createParser(bytes);
        assertEquals(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
        byte[] b = p.getBinaryValue();
        assertNotNull(b);
        assertEquals(16, b.length);
        p.close();
    }

    public void testWithNestedMaps() throws IOException
    {
        Map<Object,Object> map = new HashMap<Object,Object>();
        map.put("foo", Collections.singletonMap("", "bar"));
        byte[] bytes = MAPPER.writeValueAsBytes(map);
        JsonNode n = MAPPER.readTree(new ByteArrayInputStream(bytes));
        assertTrue(n.isObject());
        assertEquals(1, n.size());
        JsonNode n2 = n.get("foo");
        assertNotNull(n2);
        assertEquals(1, n2.size());
        JsonNode n3 = n2.get("");
        assertNotNull(n3);
        assertTrue(n3.isTextual());
    }

    // for [dataformat-smile#26]
    public void testIssue26ArrayOutOfBounds() throws Exception
    {
        SmileFactory f = new SmileFactory();
        ObjectMapper mapper = new ObjectMapper(new SmileFactory());
        byte[] buffer = _generateHugeDoc(f);

        // split the buffer in two smaller buffers
        int len = 160;
        byte[] buf1 = new byte[len];
        byte[] buf2 = new byte[buffer.length - len];
        System.arraycopy(buffer, 0, buf1, 0, len);
        System.arraycopy(buffer, len, buf2, 0, buffer.length - len);

        // aggregate the two buffers via a SequenceInputStream
        ByteArrayInputStream in1 = new ByteArrayInputStream(buf1);
        ByteArrayInputStream in2 = new ByteArrayInputStream(buf2);
        SequenceInputStream inputStream = new SequenceInputStream(in1, in2);

        JsonNode jsonNode = mapper.readTree(inputStream);
        assertNotNull(jsonNode);

        // let's actually verify
        ArrayNode arr = (ArrayNode) jsonNode;
        assertEquals(26, arr.size());
    }

    private byte[] _generateHugeDoc(SmileFactory f) throws IOException
    {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        JsonGenerator g = f.createGenerator(b);
        g.writeStartArray();

        for (int c = 'a'; c <= 'z'; ++c) {
            g.writeStartObject();
            for (int ix = 0; ix < 1000; ++ix) {
                String name = "" + ((char) c) + ix;
                g.writeNumberField(name, ix);
            }
            g.writeEndObject();
        }
        g.writeEndArray();
        g.close();
        return b.toByteArray();
    }
}
