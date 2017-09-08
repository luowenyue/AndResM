/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.reginald.andresm.arsc;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

/** A package chunk is a collection of resource data types within a package. */
public final class PackageChunk extends ChunkWithChunks {

    /** Offset in bytes, from the start of the chunk, where {@code typeStringsOffset} can be found. */
    private static final int TYPE_OFFSET_OFFSET = 268;

    /** Offset in bytes, from the start of the chunk, where {@code keyStringsOffset} can be found. */
    private static final int KEY_OFFSET_OFFSET = 276;

    /** The package id if this is a base package, or 0 if not a base package. */
    private int id;

    /** The name of the package. */
    private String packageName;

    /** The offset (from {@code offset}) in the original buffer where type strings start. */
    private final int typeStringsOffset;

    /** The index into the type string pool of the last public type. */
    private final int lastPublicType;

    /** An offset to the string pool that contains the key strings for this package. */
    private final int keyStringsOffset;

    /** The index into the key string pool of the last public key. */
    private final int lastPublicKey;

    /** An offset to the type ID(s). This is undocumented in the original code. */
    private final int typeIdOffset;

    /** Contains a mapping of a type index to its {@link TypeSpecChunk}. */
    private final Map<Integer, TypeSpecChunk> typeSpecs = new HashMap<>();

    /** Contains a mapping of a type index to all of the {@link TypeChunk} with that index. */
    private final Multimap<Integer, TypeChunk> types = HashMultimap.create();

    private StringPoolChunk keyStringPool;

    private StringPoolChunk typeStringPool;

    private LibraryChunk libraryChunk;

    @Override
    public String toArscString() {
        StringBuilder typeSpecsStr = new StringBuilder();
        typeSpecsStr.append("[\n");
        for (Map.Entry<Integer, TypeSpecChunk> mapEntry : typeSpecs.entrySet()) {
            typeSpecsStr.append(String.format("type_id = %s, typeSpecs_chunk = %s",
                    Integer.toHexString(mapEntry.getKey()), mapEntry.getValue().toArscString()));
            typeSpecsStr.append("\n");
        }
        typeSpecsStr.append("]\n");

        StringBuilder typesStr = new StringBuilder();
        typesStr.append("[\n");
        for (Map.Entry<Integer, TypeChunk> mapEntry : types.entries()) {
            typesStr.append(String.format("type_id = %s, type_chunk = %s",
                    Integer.toHexString(mapEntry.getKey()), mapEntry.getValue().toArscString()));
            typesStr.append("\n");
        }
        typesStr.append("]\n");

        StringBuilder stringsStr = new StringBuilder();
        stringsStr.append("[\n");

        stringsStr.append(String.format("keyStringPool = %s", keyStringPool != null ? keyStringPool.toArscString() : "NULL"));
        stringsStr.append("\n");

        stringsStr.append(String.format("typeSpecsStr = %s", typeStringPool != null ? typeStringPool.toArscString() : "NULL"));
        stringsStr.append("\n");


        stringsStr.append("]\n");

        return String.format("PackageChunk[ %s id = %s, packageName = %s(%s), typeStringsOffset = %s, lastPublicType = %s, keyStringsOffset = %s, lastPublicKey = %s, typeIdOffset = %s, \n" +
                        "library = %s \n" +
                        "strings = %s, \n" +
                        "typeSpecs = %s, \n" +
                        "types = %s ]", super.toArscString(), Integer.toHexString(id), packageName, byteArrayToHex(packageName.getBytes(Charset.forName("UTF-16LE"))), Integer.toHexString(typeStringsOffset),
                Integer.toHexString(lastPublicType), Integer.toHexString(keyStringsOffset), Integer.toHexString(lastPublicKey), Integer.toHexString(typeIdOffset),
                libraryChunk != null ? libraryChunk.toArscString() : "NULL", stringsStr, typeSpecsStr, typesStr);
    }

    public static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for(byte b: a)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

    protected PackageChunk(ByteBuffer buffer, @Nullable Chunk parent) {
        super(buffer, parent);
        id = buffer.getInt();
        packageName = PackageUtils.readPackageName(buffer, buffer.position());
        typeStringsOffset = buffer.getInt();
        lastPublicType = buffer.getInt();
        keyStringsOffset = buffer.getInt();
        lastPublicKey = buffer.getInt();
        typeIdOffset = buffer.getInt();
    }

    @Override
    protected void init(ByteBuffer buffer) {
        super.init(buffer);
        for (Chunk chunk : getChunks()) {
            if (chunk instanceof TypeChunk) {
                TypeChunk typeChunk = (TypeChunk) chunk;
                types.put(typeChunk.getId(), typeChunk);
            } else if (chunk instanceof TypeSpecChunk) {
                TypeSpecChunk typeSpecChunk = (TypeSpecChunk) chunk;
                typeSpecs.put(typeSpecChunk.getId(), typeSpecChunk);
            } else if ((chunk instanceof StringPoolChunk)) {
                StringPoolChunk stringPoolChunk = (StringPoolChunk) chunk;
                System.out.println("init() packageName = " + packageName +
                        " ,chunk.offset = " + chunk.offset +
                        " ,keyStringsOffset = " + (keyStringsOffset + this.offset) +
                        " ,typeStringsOffset = " + (typeStringsOffset + this.offset) +
                        " ,typeIdOffset = " + typeIdOffset);
                if (chunk.offset == keyStringsOffset + this.offset) {
                    keyStringPool = stringPoolChunk;
                } else if (chunk.offset == typeStringsOffset + this.offset) {
                    typeStringPool = stringPoolChunk;
                } else {
                    throw new IllegalStateException("Unknown StringChunk in PackageChunk! " + stringPoolChunk);
                }
            } else if (chunk instanceof LibraryChunk) {
                libraryChunk = (LibraryChunk) chunk;
            } else {
                throw new IllegalStateException(
                        String.format("PackageChunk contains an unexpected chunk: %s", chunk.getClass()));
            }
        }
    }

    /** Returns the package id if this is a base package, or 0 if not a base package. */
    public int getId() {
        return id;
    }

    public void setId(int newId) {
        id = newId;
    }

    /**
     * Returns the string pool that contains the names of the resources in this package.
     */
    public StringPoolChunk getKeyStringPool() {
        return keyStringPool;
    }

    /**
     * Returns the string pool that contains the type strings for this package, such as "layout",
     * "string", "color".
     */
    public StringPoolChunk getTypeStringPool() {
        return typeStringPool;
    }

    /** Returns all {@link TypeChunk} in this package. */
    public Collection<TypeChunk> getTypeChunks() {
        return types.values();
    }

    /**
     * For a given type id, returns the {@link TypeChunk} objects that match that id. The type id is
     * the 1-based index of the type in the type string pool (returned by {@link #getTypeStringPool}).
     * @param id The 1-based type id to return {@link TypeChunk} objects for.
     * @return The matching {@link TypeChunk} objects, or an empty collection if there are none.
     */
    public Collection<TypeChunk> getTypeChunks(int id) {
        return types.get(id);
    }

    /**
     * For a given type, returns the {@link TypeChunk} objects that match that type
     * (e.g. "attr", "id", "string", ...).
     * @param type The type to return {@link TypeChunk} objects for.
     * @return The matching {@link TypeChunk} objects, or an empty collection if there are none.
     */
    public Collection<TypeChunk> getTypeChunks(String type) {
        StringPoolChunk typeStringPool = Preconditions.checkNotNull(getTypeStringPool());
        return getTypeChunks(typeStringPool.indexOf(type) + 1);  // Convert 0-based index to 1-based
    }

    /** Returns all {@link TypeSpecChunk} in this package. */
    public Collection<TypeSpecChunk> getTypeSpecChunks() {
        return typeSpecs.values();
    }

    /** For a given (1-based) type id, returns the {@link TypeSpecChunk} matching it. */
    public TypeSpecChunk getTypeSpecChunk(int id) {
        return Preconditions.checkNotNull(typeSpecs.get(id));
    }

    /**
     * For a given {@code type}, returns the {@link TypeSpecChunk} that matches it
     * (e.g. "attr", "id", "string", ...).
     */
    public TypeSpecChunk getTypeSpecChunk(String type) {
        StringPoolChunk typeStringPool = Preconditions.checkNotNull(getTypeStringPool());
        return getTypeSpecChunk(typeStringPool.indexOf(type) + 1);  // Convert 0-based index to 1-based
    }

    /** Returns the name of this package. */
    public String getPackageName() {
        return packageName;
    }

    @Override
    protected Type getType() {
        return Chunk.Type.TABLE_PACKAGE;
    }

    @Override
    protected void writeHeader(ByteBuffer output) {
        output.putInt(id);
        PackageUtils.writePackageName(output, packageName);
        output.putInt(0);  // typeStringsOffset. This value can't be computed here.
        output.putInt(lastPublicType);
        output.putInt(0);  // keyStringsOffset. This value can't be computed here.
        output.putInt(lastPublicKey);
        output.putInt(typeIdOffset);
    }

    @Override
    protected void writePayload(DataOutput output, ByteBuffer header, boolean shrink)
            throws IOException {
        int typeOffset = typeStringsOffset;
        int keyOffset = keyStringsOffset;
        int payloadOffset = 0;
        for (Chunk chunk : getChunks()) {
            System.out.println("writePayload() chunk = " + chunk + ", payloadOffset = " + payloadOffset);
            if (chunk == getTypeStringPool()) {
                typeOffset = payloadOffset + getHeaderSize();
            } else if (chunk == getKeyStringPool()) {
                keyOffset = payloadOffset + getHeaderSize();
            }
            byte[] chunkBytes = chunk.toByteArray(shrink);
            output.write(chunkBytes);
            payloadOffset += writePad(output, chunkBytes.length);
        }
        System.out.println("writePayload() typeOffset = " + typeOffset + " , keyOffset = " + keyOffset);
        header.putInt(TYPE_OFFSET_OFFSET, typeOffset);
        header.putInt(KEY_OFFSET_OFFSET, keyOffset);
    }
}