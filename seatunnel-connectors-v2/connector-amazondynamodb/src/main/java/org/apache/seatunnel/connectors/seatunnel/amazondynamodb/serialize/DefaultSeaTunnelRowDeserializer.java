/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.connectors.seatunnel.amazondynamodb.serialize;

import org.apache.seatunnel.api.table.type.ArrayType;
import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.DecimalType;
import org.apache.seatunnel.api.table.type.LocalTimeType;
import org.apache.seatunnel.api.table.type.MapType;
import org.apache.seatunnel.api.table.type.PrimitiveByteArrayType;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;

import lombok.AllArgsConstructor;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
public class DefaultSeaTunnelRowDeserializer implements SeaTunnelRowDeserializer {

    private final SeaTunnelRowType typeInfo;

    @Override
    public SeaTunnelRow deserialize(Map<String, AttributeValue> item) {
        SeaTunnelDataType<?>[] seaTunnelDataTypes = typeInfo.getFieldTypes();
        return new SeaTunnelRow(convertRow(seaTunnelDataTypes, item).toArray());
    }

    private List<Object> convertRow(SeaTunnelDataType<?>[] seaTunnelDataTypes, Map<String, AttributeValue> item) {
        List<Object> fields = new ArrayList<>();
        String[] fieldNames = typeInfo.getFieldNames();
        for (int i = 0; i < seaTunnelDataTypes.length; i++) {
            SeaTunnelDataType<?> seaTunnelDataType = seaTunnelDataTypes[i];
            AttributeValue attributeValue = item.get(fieldNames[i]);
            fields.add(convert(seaTunnelDataType, attributeValue));
        }
        return fields;
    }

    private Object convert(SeaTunnelDataType<?> seaTunnelDataType, AttributeValue attributeValue) {
        if (attributeValue.type().equals(AttributeValue.Type.NUL)) {
            return null;
        } else if (BasicType.BOOLEAN_TYPE.equals(seaTunnelDataType)) {
            return attributeValue.bool();
        } else if (BasicType.BYTE_TYPE.equals(seaTunnelDataType)) {
            if (attributeValue.n() != null) {
                return Byte.parseByte(attributeValue.n());
            }
            return attributeValue.s().getBytes(StandardCharsets.UTF_8)[0];
        } else if (BasicType.SHORT_TYPE.equals(seaTunnelDataType)) {
            return Short.parseShort(attributeValue.n());
        } else if (BasicType.INT_TYPE.equals(seaTunnelDataType)) {
            return Integer.parseInt(attributeValue.n());
        } else if (BasicType.LONG_TYPE.equals(seaTunnelDataType)) {
            return Long.parseLong(attributeValue.n());
        } else if (seaTunnelDataType instanceof DecimalType) {
            return new BigDecimal(attributeValue.n());
        } else if (BasicType.FLOAT_TYPE.equals(seaTunnelDataType)) {
            return Float.parseFloat(attributeValue.n());
        } else if (BasicType.DOUBLE_TYPE.equals(seaTunnelDataType)) {
            return Double.parseDouble(attributeValue.n());
        } else if (BasicType.STRING_TYPE.equals(seaTunnelDataType)) {
            return attributeValue.s();
        } else if (LocalTimeType.LOCAL_TIME_TYPE.equals(seaTunnelDataType)) {
            return LocalTime.parse(attributeValue.s());
        } else if (LocalTimeType.LOCAL_DATE_TYPE.equals(seaTunnelDataType)) {
            return LocalDate.parse(attributeValue.s());
        } else if (LocalTimeType.LOCAL_DATE_TIME_TYPE.equals(seaTunnelDataType)) {
            return LocalDateTime.parse(attributeValue.s());
        } else if (PrimitiveByteArrayType.INSTANCE.equals(seaTunnelDataType)) {
            return attributeValue.b().asByteArray();
        } else if (seaTunnelDataType instanceof MapType) {
            Map<String, Object> seatunnelMap = new HashMap<>();
            attributeValue.m().forEach((s, attributeValueInfo) -> {
                seatunnelMap.put(s, convert(((MapType) seaTunnelDataType).getValueType(), attributeValueInfo));
            });
            return seatunnelMap;
        } else if (seaTunnelDataType instanceof ArrayType) {
            Object array = Array.newInstance(String.class, attributeValue.l().size());
            if (attributeValue.hasL()) {
                List<AttributeValue> datas = attributeValue.l();
                array = Array.newInstance(((ArrayType<?, ?>) seaTunnelDataType).getElementType().getTypeClass(), attributeValue.l().size());
                for (int index = 0; index < datas.size(); index++) {
                    Array.set(array, index, convert(((ArrayType<?, ?>) seaTunnelDataType).getElementType(), datas.get(index)));
                }
            } else if (attributeValue.hasSs()) {
                List<String> datas = attributeValue.ss();
                for (int index = 0; index < datas.size(); index++) {
                    Array.set(array, index, AttributeValue.fromS(datas.get(index)));
                }
            } else if (attributeValue.hasNs()) {
                List<String> datas = attributeValue.ns();
                for (int index = 0; index < datas.size(); index++) {
                    Array.set(array, index, AttributeValue.fromS(datas.get(index)));
                }
            } else if (attributeValue.hasBs()) {
                List<SdkBytes> datas = attributeValue.bs();
                for (int index = 0; index < datas.size(); index++) {
                    Array.set(array, index, AttributeValue.fromB(datas.get(index)));
                }
            }
            return array;
        } else {
            throw new IllegalStateException("Unexpected value: " + seaTunnelDataType);
        }
    }

}
