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
package consulo.cpp.lang.version;

/**
 * Language features a {@link CLanguageVersion} may have. A version gains them in its constructor, and a newer
 * version inherits those of the one it extends.
 *
 * @author VISTALL
 * @since 2026-10-19
 */
public enum CLanguageFeature {
    BOOL_KEYWORDS("bool, true and false keywords"),
    CLASSES("Classes"),
    NAMESPACES("Namespaces"),
    NEW_EXPRESSION("new and delete expressions"),
    TEMPLATES("Templates"),
    EXCEPTIONS("Exceptions"),
    INLINE("inline functions"),
    RESTRICT("restrict qualifier"),
    TYPEOF("typeof"),
    NULLPTR("nullptr"),
    CONSTEXPR("constexpr"),
    STATIC_ASSERT("static_assert keyword"),
    ALIGNMENT("alignof and alignas keywords"),
    THREAD_LOCAL("thread_local keyword"),
    DECLTYPE("decltype"),
    NOEXCEPT("noexcept"),
    UNICODE_CHAR_TYPES("char16_t and char32_t"),
    RAW_STRINGS("Raw string literals"),
    /**
     * The kernel launches of CUDA and HIP, {@code kernel<<<grid, block>>>(args)}: no C or C++ standard has them, the
     * versions of those dialects add them.
     */
    KERNEL_LAUNCH("Kernel launches, kernel<<<grid, block>>>(args)");

    private final String myName;

    CLanguageFeature(String name) {
        myName = name;
    }

    public String getName() {
        return myName;
    }
}
