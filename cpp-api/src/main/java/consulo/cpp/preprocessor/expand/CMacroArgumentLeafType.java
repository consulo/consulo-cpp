/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package consulo.cpp.preprocessor.expand;

import consulo.language.ast.IElementType;
import consulo.language.impl.ast.ForeignLeafType;

/**
 * A token of a macro expansion an argument of the call passed: {@code calc::add} of {@code EXPECT_EQ(calc::add(2, 3), 5)}.
 * Like every token of an expansion it has no length in the file, but it knows where in the call it was written, so the
 * name there can be clicked, found and renamed as the code it expands into.
 *
 * @author VISTALL
 * @since 2026-11-02
 */
public final class CMacroArgumentLeafType extends ForeignLeafType {
    private final int myArgumentOffset;

    public CMacroArgumentLeafType(IElementType delegate, CharSequence value, int argumentOffset) {
        super(delegate, value);
        myArgumentOffset = argumentOffset;
    }

    /**
     * Where the token is written, from the start of the macro call.
     */
    public int getArgumentOffset() {
        return myArgumentOffset;
    }
}
