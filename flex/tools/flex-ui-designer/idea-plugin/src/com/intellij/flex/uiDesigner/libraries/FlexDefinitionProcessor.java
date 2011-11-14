package com.intellij.flex.uiDesigner.libraries;

import com.google.common.base.Charsets;
import com.intellij.flex.uiDesigner.abc.*;
import com.intellij.javascript.flex.mxml.FlexNameAlias;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.CharArrayUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.util.Map;

public class FlexDefinitionProcessor implements DefinitionProcessor {
  private static final String STYLE_PROTO_CHAIN = "mx.styles:StyleProtoChain";
  private static final String SKINNABLE_COMPONENT = "spark.components.supportClasses:SkinnableComponent";

  private static final char OVERLOADED_AND_BACKED_CLASS_MARK = 'F';

  @Override
  public void process(CharSequence name, ByteBuffer buffer, Definition definition, Map<CharSequence, Definition> definitionMap) throws IOException {
    if (StringUtil.equals(name, STYLE_PROTO_CHAIN)) {
      changeAbcName(STYLE_PROTO_CHAIN, buffer);
      flipDefinition(definition, definitionMap, STYLE_PROTO_CHAIN);
    }
    else if (StringUtil.equals(name, SKINNABLE_COMPONENT)) {
      changeAbcName(SKINNABLE_COMPONENT, buffer);
      flipDefinition(definition, definitionMap, SKINNABLE_COMPONENT);
    }
    else if (StringUtil.equals(name, "mx.containers:Panel")) {
      definition.doAbcData.abcModifier = new MethodAccessModifier("setControlBar");
    }
  }

  private static void flipDefinition(Definition definition, Map<CharSequence, Definition> definitionMap, String name) {
    // don't remove old entry from map, it may be requred before we inject
    int i = name.indexOf(':');
    String newName = name.substring(0, i + 1) + OVERLOADED_AND_BACKED_CLASS_MARK + name.substring(i + 2);
    definitionMap.put(newName, definition);
    //definition.name = newName;
  }

  private static void changeAbcName(final String name, ByteBuffer buffer) throws IOException {
    final int oldPosition = buffer.position();
    buffer.position(buffer.position() + 4 + name.length() + 1 /* null-terminated string */);
    parseCPoolAndRename(name.substring(name.indexOf(':') + 1), buffer);

    // modify abcname
    buffer.position(oldPosition + 4 + 10);
    buffer.put((byte)OVERLOADED_AND_BACKED_CLASS_MARK);
    buffer.position(oldPosition);
  }

  private static void parseCPoolAndRename(String from, ByteBuffer buffer) throws IOException {
    buffer.position(buffer.position() + 4);

    int n = AbcUtil.readU32(buffer);
    while (n-- > 1) {
      AbcUtil.readU32(buffer);
    }

    n = AbcUtil.readU32(buffer);
    while (n-- > 1) {
      AbcUtil.readU32(buffer);
    }

    n = AbcUtil.readU32(buffer);
    if (n != 0) {
      buffer.position(buffer.position() + ((n - 1) * 8));
    }

    n = AbcUtil.readU32(buffer);
    final CharsetEncoder charsetEncoder = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(
      CodingErrorAction.REPLACE);
    while (n-- > 1) {
      int l = AbcUtil.readU32(buffer);
      buffer.limit(buffer.position() + l);
      buffer.mark();
      final CharBuffer charBuffer = Charsets.UTF_8.decode(buffer);
      buffer.limit(buffer.capacity());
      final int index = CharArrayUtil.indexOf(charBuffer, from, 0);
      if (index == -1) {
        continue;
      }

      charBuffer.put(index, OVERLOADED_AND_BACKED_CLASS_MARK);
      buffer.reset();
      charsetEncoder.encode(charBuffer, buffer, true);
      charsetEncoder.reset();
    }
  }

  private static class MethodAccessModifier extends AbcModifierBase {
    private final String fieldName;

    private MethodAccessModifier(String fieldName) {
      this.fieldName = fieldName;
    }

    @Override
    public boolean writeMethodTraitName(int name, int traitKind, DataBuffer in, Encoder encoder) {
      if (isNotOverridenMethod(traitKind)) {
        if (encoder.changeAccessModifier(fieldName, name, in)) {
          return true;
        }
      }

      return false;
    }
  }

  private static class VarAccessModifier extends AbcModifierBase {
    private final String fieldName;

    private VarAccessModifier(String fieldName) {
      this.fieldName = fieldName;
    }

    @Override
    public boolean writeSlotTraitName(int name, int traitKind, DataBuffer in, Encoder encoder) {
      if (isVar(traitKind)) {
        if (encoder.changeAccessModifier(fieldName, name, in)) {
          return true;
        }
      }

      return false;
    }
  }
}