package joshua.decoder.ff;

import java.util.List;

import joshua.decoder.ff.state_maintenance.DPState;
import joshua.decoder.ff.tm.Rule;
import joshua.decoder.chart_parser.SourcePath;
import joshua.decoder.hypergraph.HGNode;

/**
 * 
 * @author Zhifei Li <zhifei.work@gmail.com>
 * @author Matt Post <post@cs.jhu.edu>
 */
public final class WordPenaltyFF extends StatelessFF {

  private float OMEGA = -(float) Math.log10(Math.E); // -0.435
//  private float OMEGA = 1;  

  public WordPenaltyFF(final FeatureVector weights) {
    super(weights, "WordPenalty", "");
  }

  public WordPenaltyFF(final FeatureVector weights, float value) {
    super(weights, "WordPenalty", "");
    OMEGA = value;
  }
  
  @Override
  public DPState compute(Rule rule, List<HGNode> tailNodes, int i, int j, SourcePath sourcePath,
      int sentID, Accumulator acc) {
    
    if (rule != null)
      acc.add(name, OMEGA * (rule.getEnglish().length - rule.getArity()));

    return null;
  }
}
