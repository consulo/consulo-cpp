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
package consulo.cpp.lang.psi;

/**
 * The execution configuration of a CUDA kernel launch, {@code <<<grid, block, sharedMemory, stream>>>} between the
 * kernel and its arguments, {@code add<<<blocks, 256>>>(a, b, c)}: the expressions it holds are the children.
 *
 * @author VISTALL
 * @since 2026-11-13
 */
public interface CPsiKernelLaunchConfiguration extends CPsiElement {
}
