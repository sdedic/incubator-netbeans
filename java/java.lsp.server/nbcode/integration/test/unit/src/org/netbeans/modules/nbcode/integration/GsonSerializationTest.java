/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.netbeans.modules.nbcode.integration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.netbeans.junit.NbTestCase;

/**
 *
 * @author sdedic
 */
public class GsonSerializationTest extends NbTestCase {
    
    public GsonSerializationTest(String name) {
        super(name);
    }
    
    public void testDeserializeWithEnumSet() throws Exception {
        GsonBuilder b = new GsonBuilder();
        b.registerTypeAdapter(new TypeToken<EnumSet<?>>() {}.getType(), new InstanceCreator<EnumSet>() {
            @Override
            public EnumSet createInstance(Type type) {
                Type[] types = (((ParameterizedType) type).getActualTypeArguments());
                return EnumSet.noneOf((Class<Enum>) types[0]);
            }
        });
        b.registerTypeAdapterFactory(new LowercaseEnumTypeAdapterFactory());
        
        
        Gson gson = b.create();
        String json = gson.toJson(new Structure(EnumSet.of(Options.skipConflicts)));
        
        
        Structure s = gson.fromJson(json, Structure.class);
        System.err.println(s);
        
    }
    
    public class LowercaseEnumTypeAdapterFactory implements TypeAdapterFactory {

        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            Class<T> rawType = (Class<T>) type.getRawType();
            if (!rawType.isEnum()) {
                return null;
            }

            final Map<String, T> lowercaseToConstant = new HashMap<String, T>();
            for (T constant : rawType.getEnumConstants()) {
                lowercaseToConstant.put(toLowercase(constant), constant);
            }

            return new TypeAdapter<T>() {
                public void write(JsonWriter out, T value) throws IOException {
                    if (value == null) {
                        out.nullValue();
                    } else {
                        out.value(toLowercase(value));
                    }
                }

                public T read(JsonReader reader) throws IOException {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull();
                        return null;
                    } else {
                        return lowercaseToConstant.get(toLowercase(reader.nextString()));
                    }
                }
            };
        }

        private String toLowercase(Object o) {
            return o.toString().toLowerCase(Locale.US);
        }
    }

    public static class Structure {
        private final EnumSet<Options> options;

        public Structure(EnumSet<Options> options) {
            this.options = options;
        }

        public EnumSet<Options> getOptions() {
            return options;
        }

    }

    /**
     * Additional options that affect how the operation is performed. Some options only affect
     * certain operations.
     */
    public enum Options {
        /**
         * Skip silently dependencies that exists (add) or do not exist (remove)
         */
        skipConflicts,
        
        /**
         * Accept any other versions (the dependency matches the group:artifact:classifier regardless of version
         */
        ignoreVersions,
    }
    
}
