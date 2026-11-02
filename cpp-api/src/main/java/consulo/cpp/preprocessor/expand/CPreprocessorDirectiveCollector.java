package consulo.cpp.preprocessor.expand;

import consulo.application.ApplicationManager;
import consulo.cpp.preprocessor.psi.impl.CPreprocessorOuterLanguageElement;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.impl.ast.*;
import consulo.language.impl.psi.template.RangeCollectorImpl;
import consulo.language.impl.psi.template.TemplateDataElementType;
import consulo.language.impl.psi.template.TreePatcher;
import consulo.language.util.CharTable;
import consulo.logging.Logger;
import consulo.logging.attachment.AttachmentFactory;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author VISTALL
 * @see RangeCollectorImpl
 * @since 13:54/2020-08-15
 */
public class CPreprocessorDirectiveCollector {
    static final Key<CPreprocessorDirectiveCollector> OUTER_ELEMENT_RANGES = Key.create("template.parser.outer.element.handler");

    final static class RangeToRemove extends TextRange {
        /**
         * We need this text to propagate dummy strings through lazy parseables. If this text is null, dummy identifier won't be propagated.
         */
        public final @Nullable CharSequence myTextToRemove;

        RangeToRemove(int startOffset, @NotNull CharSequence text) {
            super(startOffset, startOffset + text.length());
            myTextToRemove = text;
        }

        RangeToRemove(int startOffset, int endOffset) {
            super(startOffset, endOffset);
            myTextToRemove = null;
        }

        @Override
        public @NotNull TextRange shiftLeft(int delta) {
            if (delta == 0) {
                return this;
            }
            return myTextToRemove != null
                ? new RangeToRemove(getStartOffset() - delta, myTextToRemove)
                : new RangeToRemove(getStartOffset() - delta, getEndOffset() - delta);
        }

        @Override
        public String toString() {
            return "RangeToRemove" + super.toString();
        }
    }

    static final class InsertionRange extends TextRange {

        InsertionRange(int startOffset, int endOffset) {
            super(startOffset, endOffset);
        }

        @Override
        public @NotNull TextRange shiftLeft(int delta) {
            if (delta == 0) {
                return this;
            }
            return new InsertionRange(getStartOffset() - delta, getEndOffset() - delta);
        }

        @Override
        public String toString() {
            return "InsertionRange" + super.toString();
        }
    }

    private final List<TextRange> myOuterAndRemoveRanges = new ArrayList<>();

    public void addOuterRange(@NotNull TextRange newRange) {
        addOuterRange(newRange, false);
    }

    public void addOuterRange(@NotNull TextRange newRange, boolean isInsertion) {
        if (newRange.isEmpty()) {
            return;
        }
        assertRangeOrder(newRange);

        if (!myOuterAndRemoveRanges.isEmpty()) {
            int lastItemIndex = myOuterAndRemoveRanges.size() - 1;
            TextRange lastRange = myOuterAndRemoveRanges.get(lastItemIndex);
            if (lastRange.getEndOffset() == newRange.getStartOffset() && !(lastRange instanceof RangeToRemove)) {
                TextRange joinedRange =
                    lastRange instanceof InsertionRange || isInsertion
                        ? new InsertionRange(lastRange.getStartOffset(), newRange.getEndOffset())
                        : TextRange.create(lastRange.getStartOffset(), newRange.getEndOffset());
                myOuterAndRemoveRanges.set(lastItemIndex, joinedRange);
                return;
            }
        }
        myOuterAndRemoveRanges.add(isInsertion ? new InsertionRange(newRange.getStartOffset(), newRange.getEndOffset()) : newRange);
    }

    private void assertRangeOrder(@NotNull TextRange newRange) {
        TextRange range = ContainerUtil.getLastItem(myOuterAndRemoveRanges);
        assert range == null || newRange.getStartOffset() >= range.getStartOffset();
    }

    @NotNull
    CharSequence applyTemplateDataModifications(@NotNull CharSequence sourceCode, @NotNull CTemplateDataModifications modifications) {
        assert myOuterAndRemoveRanges.isEmpty();
        List<TextRange> ranges = modifications.myOuterAndRemoveRanges;
        if (ranges.isEmpty()) {
            return sourceCode;
        }
        for (TextRange range : ranges) {
            if (range instanceof CPreprocessorDirectiveCollector.RangeToRemove) {
                if (range.isEmpty()) {
                    continue;
                }
                assertRangeOrder(range);
                CharSequence textToRemove = ((CPreprocessorDirectiveCollector.RangeToRemove) range).myTextToRemove;
                assert textToRemove != null;
                myOuterAndRemoveRanges.add(new CPreprocessorDirectiveCollector.RangeToRemove(range.getStartOffset(), textToRemove));
            }
            else {
                addOuterRange(range, range instanceof CPreprocessorDirectiveCollector.InsertionRange);
            }
        }

        return applyOuterAndRemoveRanges(sourceCode);
    }

    @NotNull
    private StringBuilder applyOuterAndRemoveRanges(CharSequence chars) {
        StringBuilder stringBuilder = new StringBuilder(chars);
        int shift = 0;
        for (TextRange outerElementRange : myOuterAndRemoveRanges) {
            if (outerElementRange instanceof CPreprocessorDirectiveCollector.RangeToRemove) {
                CharSequence textToRemove = ((CPreprocessorDirectiveCollector.RangeToRemove) outerElementRange).myTextToRemove;
                if (textToRemove != null) {
                    stringBuilder.insert(outerElementRange.getStartOffset() + shift, textToRemove);
                    shift += textToRemove.length();
                }
            }
            else {
                stringBuilder.delete(outerElementRange.getStartOffset() + shift,
                    outerElementRange.getEndOffset() + shift);
                shift -= outerElementRange.getLength();
            }
        }
        return stringBuilder;
    }

    /**
     * Builds the merged tree with inserting outer language elements and removing additional elements according to the ranges from rangeCollector.
     * Chameleons which implement {@link TemplateDataElementType.TemplateAwareElementType} are treated as leaves, i.e. they are not expanded
     * but their contents are modified considering outer elements and RangeCollector is put to their user data to be applied when this
     * chameleon is expanded.
     *
     * @param language
     * @param templateFileElement parsed template data language file without outer elements and with possible custom additions
     * @param sourceCode          original source code (include template data language and template language)
     */
    public void insertOuterElementsAndRemoveRanges(@NotNull TreeElement templateFileElement,
                                                   @NotNull CharSequence sourceCode,
                                                   @NotNull CharTable charTable,
                                                   @NotNull Language language) {
        TreePatcher templateTreePatcher = TreePatcher.forLanguage(language);

        TreeElement currentLeafOrLazyParseable = findFirstSuitableElement(templateFileElement);

        // we use manual offset counter because node.getStartOffset() is expensive here
        // offset in original text
        int currentLeafOffset = 0;

        for (TextRange rangeToProcess : myOuterAndRemoveRanges) {
            int rangeStartOffset = rangeToProcess.getStartOffset();

            // search for leaf following or intersecting range
            while (currentLeafOrLazyParseable != null &&
                currentLeafOffset + currentLeafOrLazyParseable.getTextLength() <= rangeStartOffset) {
                currentLeafOffset += currentLeafOrLazyParseable.getTextLength();
                currentLeafOrLazyParseable = findNextSuitableElement(currentLeafOrLazyParseable);
            }

            boolean addRangeToLazyParseableCollector = false;
            if (rangeToProcess instanceof CPreprocessorDirectiveCollector.RangeToRemove) {
                if (currentLeafOrLazyParseable == null) {
                    Logger.getInstance(TemplateDataElementType.RangeCollector.class).error(
                        "RangeToRemove's range is out of original text bound",
                        AttachmentFactory.get().create("myOuterAndRemoveRanges", StringUtil.join(myOuterAndRemoveRanges, TextRange::toString, ", ")),
                        AttachmentFactory.get().create("rangeToProcess", rangeToProcess.toString()),
                        AttachmentFactory.get().create("sourceCode", sourceCode.toString()));
                    continue;
                }
                currentLeafOrLazyParseable =
                    removeElementsForRange(currentLeafOrLazyParseable, currentLeafOffset, rangeToProcess, templateTreePatcher, charTable);
                if (currentLeafOrLazyParseable != null &&
                    !(currentLeafOrLazyParseable instanceof LeafElement) &&
                    ((CPreprocessorDirectiveCollector.RangeToRemove) rangeToProcess).myTextToRemove != null) {
                    addRangeToLazyParseableCollector = true;
                }
            }
            else {
                if (currentLeafOrLazyParseable instanceof LeafElement && currentLeafOffset < rangeStartOffset) {
                    int splitOffset = rangeStartOffset - currentLeafOffset;
                    currentLeafOrLazyParseable = splitAt((LeafElement) currentLeafOrLazyParseable, splitOffset, charTable);
                    currentLeafOffset = rangeStartOffset;
                }
                if (currentLeafOrLazyParseable == null) {
                    insertLastOuterElementForRange((CompositeElement) templateFileElement, rangeToProcess, sourceCode, charTable);
                }
                else {
                    currentLeafOrLazyParseable =
                        insertOuterElementFromRange(currentLeafOrLazyParseable, currentLeafOffset, rangeToProcess, sourceCode, templateTreePatcher,
                            charTable);
                    if (!(currentLeafOrLazyParseable instanceof LeafElement)) {
                        addRangeToLazyParseableCollector = true;
                    }
                }
            }
            if (addRangeToLazyParseableCollector) {
                CPreprocessorDirectiveCollector lazyParseableCollector = currentLeafOrLazyParseable.getUserData(OUTER_ELEMENT_RANGES);
                assert lazyParseableCollector != null && lazyParseableCollector != this;
                lazyParseableCollector.myOuterAndRemoveRanges.add(rangeToProcess.shiftLeft(currentLeafOffset));
            }
        }

        if (ApplicationManager.getApplication().isUnitTestMode()) {
            String after = templateFileElement.getText();
            assert after.contentEquals(sourceCode) :
                "Text presentation for the new tree must be the same: \nbefore: " + sourceCode + "\nafter: " + after;
        }
    }

    private @NotNull TreeElement insertOuterElementFromRange(@NotNull TreeElement currentLeaf,
                                                             int currentLeafOffset,
                                                             @NotNull TextRange outerElementRange,
                                                             @NotNull CharSequence sourceCode,
                                                             @NotNull TreePatcher templateTreePatcher,
                                                             @NotNull CharTable charTable) {
        CharSequence outerElementText = outerElementRange.subSequence(sourceCode);
        if (currentLeaf instanceof LazyParseableElement) {
            StringBuilder builder = new StringBuilder(currentLeaf.getText());
            builder.insert(outerElementRange.getStartOffset() - currentLeafOffset, outerElementText);
            TreeElement newElement = newLazyParseable(currentLeaf, builder.toString());
            currentLeaf.rawInsertAfterMe(newElement);
            currentLeaf.rawRemove();
            return newElement;
        }

        CharSequence charSequence = charTable.intern(outerElementText);
        CPreprocessorOuterLanguageElement newLeaf = new CPreprocessorOuterLanguageElement(charSequence);

        templateTreePatcher.insert(currentLeaf.getTreeParent(), currentLeaf, newLeaf);
        return newLeaf;
    }

    /**
     * Splits a leaf in two of its type and returns the right part, where the range being processed starts. Not
     * {@link TreePatcher#split}: the default one returns the left part, which put a directive before the whitespace
     * preceding it and shifted every offset after it.
     */
    private static LeafElement splitAt(LeafElement leaf, int offset, CharTable table) {
        CharSequence chars = leaf.getChars();
        LeafElement leftPart = ASTFactory.leaf(leaf.getElementType(), table.intern(chars, 0, offset));
        LeafElement rightPart = ASTFactory.leaf(leaf.getElementType(), table.intern(chars, offset, chars.length()));
        leaf.rawInsertAfterMe(leftPart);
        leftPart.rawInsertAfterMe(rightPart);
        leaf.rawRemove();
        return rightPart;
    }

    private static boolean isSuitableElement(@NotNull ASTNode element) {
        return element instanceof LeafElement;
    }

    private static boolean isLastRange(@NotNull List<TextRange> outerElementsRanges, @NotNull TextRange outerElementRange) {
        return outerElementsRanges.get(outerElementsRanges.size() - 1) == outerElementRange;
    }

    private void insertLastOuterElementForRange(@NotNull CompositeElement templateFileElement,
                                                @NotNull TextRange outerElementRange,
                                                @NotNull CharSequence sourceCode,
                                                @NotNull CharTable charTable) {
        assert isLastRange(myOuterAndRemoveRanges, outerElementRange) :
            "This should only happen for the last inserted range. Got " + myOuterAndRemoveRanges.lastIndexOf(outerElementRange) +
                " of " + (myOuterAndRemoveRanges.size() - 1);
        CharSequence charSequence = outerElementRange.subSequence(sourceCode);

        CPreprocessorOuterLanguageElement element = new CPreprocessorOuterLanguageElement(charSequence);
        templateFileElement.rawAddChildren(element);
    }

    /**
     * Similar to {@link TreeUtil#findFirstLeaf(ASTNode)}, but also treats collapsed lazy parseable elements as leaves and returns them.
     */
    private static
    @Nullable
    TreeElement findFirstSuitableElement(@NotNull ASTNode element) {
        if (isSuitableElement(element)) {
            return (TreeElement) element;
        }
        else {
            for (ASTNode child = element.getFirstChildNode(); child != null; child = child.getTreeNext()) {
                TreeElement leaf = findFirstSuitableElement(child);
                if (leaf != null) {
                    return leaf;
                }
            }
            return null;
        }
    }

    /**
     * Similar to {@link TreeUtil#nextLeaf(ASTNode)}, but also treats collapsed lazy parseable elements as leaves and returns them.
     */
    private static @Nullable TreeElement findNextSuitableElement(@NotNull TreeElement start) {
        TreeElement element = start;
        while (element != null) {
            TreeElement nextTree = element;
            TreeElement next = null;
            while (next == null && (nextTree = nextTree.getTreeNext()) != null) {
                next = findFirstSuitableElement(nextTree);
            }
            if (next != null) {
                return next;
            }
            element = element.getTreeParent();
        }
        return null;
    }


    @Nullable
    private TreeElement removeElementsForRange(@NotNull TreeElement startLeaf,
                                               int startLeafOffset,
                                               @NotNull TextRange rangeToRemove,
                                               @NotNull TreePatcher templateTreePatcher,
                                               @NotNull CharTable charTable) {
        @Nullable TreeElement nextLeaf = startLeaf;
        int nextLeafStartOffset = startLeafOffset;
        Collection<TreeElement> leavesToRemove = new ArrayList<>();
        while (nextLeaf != null && rangeToRemove.containsRange(nextLeafStartOffset, nextLeafStartOffset + nextLeaf.getTextLength())) {
            leavesToRemove.add(nextLeaf);
            nextLeafStartOffset += nextLeaf.getTextLength();
            nextLeaf = findNextSuitableElement(nextLeaf);
        }

        nextLeaf = splitOrRemoveRangeInsideLeafIfOverlap(nextLeaf, nextLeafStartOffset, rangeToRemove, templateTreePatcher, charTable);

        for (TreeElement element : leavesToRemove) {
            element.rawRemove();
        }
        return nextLeaf;
    }


    /**
     * Removes part the nextLeaf that intersects rangeToRemove.
     * If nextLeaf doesn't intersect rangeToRemove the method returns the nextLeaf without changes
     *
     * @return new leaf after removing the range or original nextLeaf if nothing changed
     */
    @Nullable
    private TreeElement splitOrRemoveRangeInsideLeafIfOverlap(@Nullable TreeElement nextLeaf,
                                                              int nextLeafStartOffset,
                                                              @NotNull TextRange rangeToRemove,
                                                              @NotNull TreePatcher templateTreePatcher,
                                                              @NotNull CharTable charTable) {
        if (nextLeaf == null) {
            return null;
        }
        if (nextLeafStartOffset >= rangeToRemove.getEndOffset()) {
            return nextLeaf;
        }

        if (rangeToRemove.getStartOffset() > nextLeafStartOffset) {
            return removeRange(nextLeaf, rangeToRemove.shiftLeft(nextLeafStartOffset), charTable);
        }

        int offsetToSplit = rangeToRemove.getEndOffset() - nextLeafStartOffset;
        return removeLeftPartOfLeaf(nextLeaf, offsetToSplit, templateTreePatcher, charTable);
    }

    /**
     * Splits the node according to the offsetToSplit and remove left leaf
     *
     * @return right part of the split node
     */
    @NotNull
    private TreeElement removeLeftPartOfLeaf(@NotNull TreeElement nextLeaf,
                                             int offsetToSplit,
                                             @NotNull TreePatcher templateTreePatcher,
                                             @NotNull CharTable charTable) {
        if (offsetToSplit == 0) {
            return nextLeaf;
        }
        if (!(nextLeaf instanceof LeafElement)) {
            return removeRange(nextLeaf, TextRange.from(0, offsetToSplit), charTable);
        }
        LeafElement rLeaf = splitAt((LeafElement) nextLeaf, offsetToSplit, charTable);
        LeafElement lLeaf = (LeafElement) TreeUtil.prevLeaf(rLeaf);
        assert lLeaf != null;
        lLeaf.rawRemove();
        return rLeaf;
    }


    /**
     * Removes "middle" part of the leaf and returns the new leaf with content of the right and left parts
     * e.g. if we process whitespace leaf " \n " and range "1, 2" the result will be new leaf with content "  "
     */
    @NotNull
    private TreeElement removeRange(@NotNull TreeElement leaf,
                                    @NotNull TextRange rangeToRemove,
                                    @NotNull CharTable table) {
        CharSequence chars = leaf.getChars();
        String res = rangeToRemove.replace(chars.toString(), "");
        TreeElement newLeaf =
            leaf instanceof LeafElement ? ASTFactory.leaf(leaf.getElementType(), table.intern(res)) : newLazyParseable(leaf, res);
        leaf.rawInsertBeforeMe(newLeaf);
        leaf.rawRemove();
        return newLeaf;
    }

    @NotNull
    private TreeElement newLazyParseable(@NotNull TreeElement currentLeaf, @NotNull CharSequence text) {
        TemplateDataElementType.TemplateAwareElementType elementType =
            (TemplateDataElementType.TemplateAwareElementType) currentLeaf.getElementType();
        TreeElement newElement = elementType.createTreeElement(text);
        CPreprocessorDirectiveCollector collector = currentLeaf.getUserData(OUTER_ELEMENT_RANGES);
        if (collector == null) {
            collector = new CPreprocessorDirectiveCollector();
        }
        newElement.putUserData(OUTER_ELEMENT_RANGES, collector);
        return newElement;
    }

}
