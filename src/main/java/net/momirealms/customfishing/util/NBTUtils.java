/*
 *  Copyright (C) <2022> <XiaoMoMi>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.momirealms.customfishing.util;

import de.tr7zw.changeme.nbtapi.NBTCompound;
import de.tr7zw.changeme.nbtapi.NBTItem;
import de.tr7zw.changeme.nbtapi.NBTListCompound;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import org.bukkit.configuration.MemorySection;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class NBTUtils {

    public static NBTItem setNBTToItemStack(Map<String, Object> nbt, ItemStack itemStack){
        NBTItem nbtItem = new NBTItem(itemStack);
        setTags(nbt, nbtItem);
        return nbtItem;
    }

    @SuppressWarnings("unchecked")
    public static void setTags(Map<String, Object> map, NBTCompound nbtCompound) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof MemorySection memorySection) {
                setTags(memorySection.getValues(false), nbtCompound.addCompound(key));
            } else if (value instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof String stringValue) {
                        setListValue(key, stringValue, nbtCompound);
                    } else if (o instanceof Map<?, ?> mapValue) {
                        NBTListCompound nbtListCompound = nbtCompound.getCompoundList(key).addCompound();
                        setTags((Map<String, Object>) mapValue, nbtListCompound);
                    }
                }
            } else if (value instanceof String stringValue) {
                setSingleValue(key, stringValue, nbtCompound);
            }
        }
    }

    private static void setListValue(String key, String value, NBTCompound nbtCompound) {
        String[] parts = getTypeAndData(value);
        String type = parts[0]; String data = parts[1];
        switch (type) {
            case "String" -> nbtCompound.getStringList(key).add(data);
            case "UUID" -> nbtCompound.getUUIDList(key).add(UUID.fromString(data));
            case "Double" -> nbtCompound.getDoubleList(key).add(Double.valueOf(data));
            case "Long" -> nbtCompound.getLongList(key).add(Long.valueOf(data));
            case "Float" -> nbtCompound.getFloatList(key).add(Float.valueOf(data));
            case "Int" -> nbtCompound.getIntegerList(key).add(Integer.valueOf(data));
            case "IntArray" -> {
                String[] split = data.replace("[", "").replace("]", "").replaceAll("\\s", "").split(",");
                int[] array = Arrays.stream(split).mapToInt(Integer::parseInt).toArray();
                nbtCompound.getIntArrayList(key).add(array);
            }
            default -> throw new IllegalArgumentException("Invalid value type: " + type);
        }
    }

    private static void setSingleValue(String key, String value, NBTCompound nbtCompound) {
        String[] parts = getTypeAndData(value);
        String type = parts[0]; String data = parts[1];
        switch (type) {
            case "Int" -> nbtCompound.setInteger(key, Integer.valueOf(data));
            case "String" -> nbtCompound.setString(key, data);
            case "Long" -> nbtCompound.setLong(key, Long.valueOf(data));
            case "Float" -> nbtCompound.setFloat(key, Float.valueOf(data));
            case "Double" -> nbtCompound.setDouble(key, Double.valueOf(data));
            case "Short" -> nbtCompound.setShort(key, Short.valueOf(data));
            case "Boolean" -> nbtCompound.setBoolean(key, Boolean.valueOf(data));
            case "UUID" -> nbtCompound.setUUID(key, UUID.nameUUIDFromBytes(data.getBytes()));
            case "Byte" -> nbtCompound.setByte(key, Byte.valueOf(data));
            case "ByteArray" -> {
                String[] split = splitValue(value);
                byte[] bytes = new byte[split.length];
                for (int i = 0; i < split.length; i++){
                    bytes[i] = Byte.parseByte(split[i]);
                }
                nbtCompound.setByteArray(key, bytes);
            }
            case "IntArray" -> {
                String[] split = splitValue(value);
                int[] array = Arrays.stream(split).mapToInt(Integer::parseInt).toArray();
                nbtCompound.setIntArray(key, array);
            }
            default -> throw new IllegalArgumentException("Invalid value type: " + type);
        }
    }

    public static Map<String, Object> compoundToMap(ReadWriteNBT nbtCompound){
        Map<String, Object> map = new HashMap<>();
        for (String key : nbtCompound.getKeys()) {
            switch (nbtCompound.getType(key)){
                case NBTTagByte -> map.put(key, "(Byte) " + nbtCompound.getByte(key));
                case NBTTagInt -> map.put(key, "(Int) " + nbtCompound.getInteger(key));
                case NBTTagDouble -> map.put(key, "(Double) " + nbtCompound.getDouble(key));
                case NBTTagLong -> map.put(key, "(Long) " + nbtCompound.getLong(key));
                case NBTTagFloat -> map.put(key, "(Float) " + nbtCompound.getFloat(key));
                case NBTTagShort -> map.put(key, "(Short) " + nbtCompound.getShort(key));
                case NBTTagString -> map.put(key, "(String) " + nbtCompound.getString(key));
                case NBTTagByteArray -> map.put(key, "(ByteArray) " + Arrays.toString(nbtCompound.getByteArray(key)));
                case NBTTagIntArray -> map.put(key, "(IntArray) " + Arrays.toString(nbtCompound.getIntArray(key)));
                case NBTTagCompound -> {
                    Map<String, Object> map1 = compoundToMap(nbtCompound.getCompound(key));
                    if (map1.size() != 0) map.put(key, map1);
                }
                case NBTTagList -> {
                    List<Object> list = new ArrayList<>();
                    switch (nbtCompound.getListType(key)) {
                        case NBTTagCompound -> nbtCompound.getCompoundList(key).forEach(a -> list.add(compoundToMap(a)));
                        case NBTTagInt -> nbtCompound.getIntegerList(key).forEach(a -> list.add("(Int) " + a));
                        case NBTTagDouble -> nbtCompound.getDoubleList(key).forEach(a -> list.add("(Double) " + a));
                        case NBTTagString -> nbtCompound.getStringList(key).forEach(a -> list.add("(String) " + a));
                        case NBTTagFloat -> nbtCompound.getFloatList(key).forEach(a -> list.add("(Float) " + a));
                        case NBTTagLong -> nbtCompound.getLongList(key).forEach(a -> list.add("(Long) " + a));
                        case NBTTagIntArray -> nbtCompound.getIntArrayList(key).forEach(a -> list.add("(IntArray) " + Arrays.toString(a)));
                    }
                    if (list.size() != 0) map.put(key, list);
                }
            }
        }
        return map;
    }

    private static String[] getTypeAndData(String str) {
        String[] parts = str.split("\\s+", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid value format: " + str);
        }
        String type = parts[0].substring(1, parts[0].length() - 1);
        String data = parts[1];
        return new String[]{type, data};
    }

    private static String[] splitValue(String value) {
        return value.substring(value.indexOf('[') + 1, value.lastIndexOf(']'))
                .replaceAll("\\s", "")
                .split(",");
    }
}