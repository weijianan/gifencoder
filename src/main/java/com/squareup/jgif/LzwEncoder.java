package com.squareup.jgif;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * For background, see Appendix F of the spec: http://www.w3.org/Graphics/GIF/spec-gif89a.txt
 */
class LzwEncoder {

  // Dummy values to represent special, GIF-specific instructions.
  private static final List<Integer> CLEAR_CODE = Collections.singletonList(-1);
  private static final List<Integer> END_OF_INFO = Collections.singletonList(-2);

  /**
   * The specification stipulates that code size may not exceed 12 bits.
   */
  private static final int MAX_CODE_TABLE_SIZE = 1 << 12;

  private final int numColors;
  private final BitSet outputBits = new BitSet();
  private int position = 0;
  private Map<List<Integer>, Integer> codeTable;
  private int codeSize;
  private List<Integer> indexBuffer = new ArrayList<>();

  public LzwEncoder(int numColors) {
    this.numColors = numColors;
    resetCodeTableAndCodeSize();
  }

  /**
   * This computes what the spec refers to as "code size". The actual starting code size will be one
   * bit larger than this, because of the special "clear" and "end of info" codes.
   */
  public static int getMinimumCodeSize(int numColors) {
    int size = 2; // Cannot be smaller than 2.
    while (numColors > 1 << size) {
      ++size;
    }
    return size;
  }

  public byte[] encode(int[] indices) {
    writeCode(codeTable.get(CLEAR_CODE));
    for (int index : indices) {
      processIndex(index);
    }
    writeCode(codeTable.get(indexBuffer));
    writeCode(codeTable.get(END_OF_INFO));
    return toBytes();
  }

  private void processIndex(int index) {
    List<Integer> extendedIndexBuffer = append(indexBuffer, index);
    if (codeTable.containsKey(extendedIndexBuffer)) {
      indexBuffer = extendedIndexBuffer;
    } else {
      writeCode(codeTable.get(indexBuffer));
      if (codeTable.size() == MAX_CODE_TABLE_SIZE) {
        writeCode(codeTable.get(CLEAR_CODE));
        resetCodeTableAndCodeSize();
      } else {
        addCodeToTable(extendedIndexBuffer);
      }
      indexBuffer = Collections.singletonList(index);
    }
  }

  /**
   * Write the given code to the output stream.
   */
  private void writeCode(int code) {
    for (int shift = 0; shift < codeSize; ++shift) {
      boolean bit = (code >>> shift & 1) != 0;
      outputBits.set(position++, bit);
    }
  }

  /**
   * Convert our stream of bits into a byte array, as described in the spec.
   */
  private byte[] toBytes() {
    int bitCount = position;
    byte[] result = new byte[(bitCount + 7) / 8];
    for (int i = 0; i < bitCount; ++i) {
      int byteIndex = i / 8;
      int bitIndex = i % 8;
      result[byteIndex] |= (outputBits.get(i) ? 1 : 0) << bitIndex;
    }
    return result;
  }

  private void addCodeToTable(List<Integer> indices) {
    int newCode = codeTable.size();
    codeTable.put(indices, newCode);

    if (newCode == 1 << codeSize) {
      // The next code won't fit in {@code codeSize} bits, so we need to increment.
      ++codeSize;
    }
  }

  private void resetCodeTableAndCodeSize() {
    this.codeTable = defaultCodeTable();

    // We add an extra bit because of the special "clear" and "end of info" codes.
    this.codeSize = getMinimumCodeSize(numColors) + 1;
  }

  private Map<List<Integer>, Integer> defaultCodeTable() {
    Map<List<Integer>, Integer> codeTable = new HashMap<>();
    for (int i = 0; i < numColors; ++i) {
      codeTable.put(Collections.singletonList(i), i);
    }
    codeTable.put(CLEAR_CODE, codeTable.size());
    codeTable.put(END_OF_INFO, codeTable.size());
    return codeTable;
  }

  private static <T> List<T> append(List<T> list, T value) {
    ArrayList<T> result = new ArrayList<>(list);
    result.add(value);
    return result;
  }
}
