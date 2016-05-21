/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.joshua.decoder.ff.tm;

import java.util.HashSet;
import java.util.List;

import org.apache.joshua.corpus.Vocabulary;
import org.apache.joshua.decoder.JoshuaConfiguration;
import org.apache.joshua.decoder.ff.FeatureFunction;
import org.apache.joshua.decoder.phrase.PhraseTable;
import org.apache.joshua.decoder.segment_file.Token;
import org.apache.joshua.lattice.Arc;
import org.apache.joshua.lattice.Lattice;
import org.apache.joshua.lattice.Node;

import cern.colt.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Partial implementation of the <code>Grammar</code> interface that provides logic for sorting a
 * grammar.
 * <p>
 * <em>Note</em>: New classes implementing the <code>Grammar</code> interface should probably
 * inherit from this class, unless a specific sorting technique different from that implemented by
 * this class is required.
 * 
 * @author Zhifei Li
 * @author Lane Schwartz
 * @author Matt Post <post@cs.jhu.edu
 */
public abstract class AbstractGrammar implements Grammar {

  /** Logger for this class. */
  private static final Logger LOG = LoggerFactory.getLogger(AbstractGrammar.class);
  /**
   * Indicates whether the rules in this grammar have been sorted based on the latest feature
   * function values.
   */
  protected boolean sorted = false;

  /*
   * The grammar's owner, used to determine which weights are applicable to the dense features found
   * within.
   */
  protected int owner = -1;
  
  /*
   * The maximum length of a source-side phrase. Mostly used by the phrase-based decoder.
   */
  protected int maxSourcePhraseLength = -1;
  
    /**
   * Returns the longest source phrase read.
   * 
   * @return the longest source phrase read (nonterminal + terminal symbols).
   */
  @Override
  public int getMaxSourcePhraseLength() {
    return maxSourcePhraseLength;
  }
  
  @Override
  public int getOwner() {
    return owner;
  }

  /* The maximum span of the input this rule can be applied to. */
  protected int spanLimit = 1;

  protected JoshuaConfiguration joshuaConfiguration;

  /**
   * Constructs an empty, unsorted grammar.
   * 
   * @see Grammar#isSorted()
   */
  public AbstractGrammar(JoshuaConfiguration config) {
    this.joshuaConfiguration = config;
    this.sorted = false;
  }

  public AbstractGrammar(int owner, int spanLimit) {
    this.sorted = false;
    this.owner = owner;
    this.spanLimit = spanLimit;
  }

  public static final int OOV_RULE_ID = 0;

  /**
   * Cube-pruning requires that the grammar be sorted based on the latest feature functions. To
   * avoid synchronization, this method should be called before multiple threads are initialized for
   * parallel decoding
   */
  public void sortGrammar(List<FeatureFunction> models) {
    Trie root = getTrieRoot();
    if (root != null) {
      sort(root, models);
      setSorted(true);
    }
  }

  /* See Javadoc comments for Grammar interface. */
  public boolean isSorted() {
    return sorted;
  }

  /**
   * Sets the flag indicating whether this grammar is sorted.
   * <p>
   * This method is called by {@link #sortGrammar(ArrayList)} to indicate that the grammar has been
   * sorted.
   * 
   * Its scope is protected so that child classes that override <code>sortGrammar</code> will also
   * be able to call this method to indicate that the grammar has been sorted.
   * 
   * @param sorted
   */
  protected void setSorted(boolean sorted) {
    this.sorted = sorted;
    LOG.debug("This grammar is now sorted: {}",  this);
  }

  /**
   * Recursively sorts the grammar using the provided feature functions.
   * <p>
   * This method first sorts the rules stored at the provided node, then recursively calls itself on
   * the child nodes of the provided node.
   * 
   * @param node Grammar node in the <code>Trie</code> whose rules should be sorted.
   * @param models Feature function models to use during sorting.
   */
  private void sort(Trie node, List<FeatureFunction> models) {

    if (node != null) {
      if (node.hasRules()) {
        RuleCollection rules = node.getRuleCollection();
        LOG.debug("Sorting node {}", Arrays.toString(rules.getSourceSide()));

        /* This causes the rules at this trie node to be sorted */
        rules.getSortedRules(models);

        if (LOG.isDebugEnabled()) {
          StringBuilder s = new StringBuilder();
          for (Rule r : rules.getSortedRules(models)) {
            s.append("\n\t" + r.getLHS() + " ||| " + Arrays.toString(r.getFrench()) + " ||| "
                + Arrays.toString(r.getEnglish()) + " ||| " + r.getFeatureVector() + " ||| "
                + r.getEstimatedCost() + "  " + r.getClass().getName() + "@"
                + Integer.toHexString(System.identityHashCode(r)));
          }
          LOG.debug("{}", s);
        }
      }

      if (node.hasExtensions()) {
        for (Trie child : node.getExtensions()) {
          sort(child, models);
        }
      } else {
        LOG.debug("Node has 0 children to extend: {}", node);
      }
    }
  }

  // write grammar to disk
  public void writeGrammarOnDisk(String file) {
  }
  
  /**
   * Adds OOV rules for all words in the input lattice to the current grammar. Uses addOOVRule() so that
   * sub-grammars can define different types of OOV rules if needed (as is used in {@link PhraseTable}).
   * 
   * @param inputLattice the lattice representing the input sentence
   * @param featureFunctions a list of feature functions used for scoring
   */
  public static void addOOVRules(Grammar grammar, Lattice<Token> inputLattice, 
      List<FeatureFunction> featureFunctions, boolean onlyTrue) {
    /*
     * Add OOV rules; This should be called after the manual constraints have
     * been set up.
     */
    HashSet<Integer> words = new HashSet<Integer>();
    for (Node<Token> node : inputLattice) {
      for (Arc<Token> arc : node.getOutgoingArcs()) {
        // create a rule, but do not add into the grammar trie
        // TODO: which grammar should we use to create an OOV rule?
        int sourceWord = arc.getLabel().getWord();
        if (sourceWord == Vocabulary.id(Vocabulary.START_SYM)
            || sourceWord == Vocabulary.id(Vocabulary.STOP_SYM))
          continue;

        // Determine if word is actual OOV.
        if (onlyTrue && ! Vocabulary.hasId(sourceWord))
          continue;

        words.add(sourceWord);
      }
    }

    for (int sourceWord: words) 
      grammar.addOOVRules(sourceWord, featureFunctions);

    // Sort all the rules (not much to actually do, this just marks it as sorted)
    grammar.sortGrammar(featureFunctions);
  }
}
