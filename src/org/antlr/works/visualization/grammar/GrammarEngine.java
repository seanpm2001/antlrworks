package org.antlr.works.visualization.grammar;

import org.antlr.analysis.DFA;
import org.antlr.analysis.DecisionProbe;
import org.antlr.analysis.NFAState;
import org.antlr.tool.*;
import org.antlr.works.util.CancelObject;

import java.io.FileReader;
import java.util.*;

/*

[The "BSD licence"]
Copyright (c) 2004-05 Jean Bovet
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:

1. Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.
3. The name of the author may not be used to endorse or promote products
derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

public class GrammarEngine {

    protected EngineErrorListener engineErrorListener = new EngineErrorListener();

    public Grammar g;
    public List errors = new ArrayList();

    public GrammarEngine() {
    }

    public void setGrammarText(String text) throws Exception {
        g = new Grammar(text);
        g.createNFAs();
    }

    public void setGrammarFile(String filename, String file) throws Exception {
        g = new Grammar(null, filename, new FileReader(file));
        g.createNFAs();
    }

    public void analyze(CancelObject cancelObject) throws Exception {
        ANTLRErrorListener oldListener = ErrorManager.getErrorListener();
        boolean oldVerbose = DecisionProbe.verbose;

        DecisionProbe.verbose = true;
        ErrorManager.setErrorListener(engineErrorListener);

        try {
            engineErrorListener.clear();

            createLookaheadDFAs(cancelObject);
            if(cancelObject != null && cancelObject.cancel())
                return;

            buildNonDeterministicErrors();
        } catch(Exception e) {
            throw e;
        } finally {
            DecisionProbe.verbose = oldVerbose;
            ErrorManager.setErrorListener(oldListener);
        }
    }

    private void createLookaheadDFAs(CancelObject cancelObject) {
        for (int decision=1; decision<=g.getNumberOfDecisions(); decision++) {
            NFAState decisionStartState = g.getDecisionNFAStartState(decision);
            if ( decisionStartState.getNumberOfTransitions()>1 ) {
                DFA lookaheadDFA = new DFA(decisionStartState);
                g.setLookaheadDFA(decision, lookaheadDFA);
            }
            if(cancelObject != null && cancelObject.cancel())
                break;
        }
    }

    private void buildNonDeterministicErrors() {
        errors.clear();
        for (Iterator iterator = engineErrorListener.warnings.iterator(); iterator.hasNext();) {
            Object o = iterator.next();
            if ( o instanceof GrammarNonDeterminismMessage )
                errors.add(buildNonDeterministicError((GrammarNonDeterminismMessage)o));
        }
    }

    private GrammarEngineError buildNonDeterministicError(GrammarNonDeterminismMessage nondetMsg) {
        GrammarEngineError error = new GrammarEngineError();

        //System.err.println(nondetMsg.problemState);
        List nonDetAlts = nondetMsg.probe.getNonDeterministicAltsForState(nondetMsg.problemState);
        //System.out.println("Non-det. alts = "+nonDetAlts);
        //System.out.println("Start state = "+nondetMsg.probe.dfa.getNFADecisionStartState());
        error.setLine(nondetMsg.probe.dfa.getDecisionASTNode().getLine()-1);

        Set disabledAlts = nondetMsg.probe.getDisabledAlternatives(nondetMsg.problemState);
        List labels = nondetMsg.probe.getSampleNonDeterministicInputSequence(nondetMsg.problemState);
        String input = nondetMsg.probe.getInputSequenceDisplay(labels);
        error.setMessage("Decision can match input such as \""+input+"\" using multiple alternatives");
        //System.out.println("Input = "+input);

        int firstAlt = 0;
		for (Iterator iter = nonDetAlts.iterator(); iter.hasNext();) {
            Integer displayAltI = (Integer) iter.next();
            NFAState nfaStart = nondetMsg.probe.dfa.getNFADecisionStartState();

			int tracePathAlt =
				nfaStart.translateDisplayAltToWalkAlt(displayAltI.intValue());
			if ( firstAlt == 0 ) {
				firstAlt = tracePathAlt;
			}
			List path =
				nondetMsg.probe.getNFAPathStatesForAlt(firstAlt,
													   tracePathAlt,
													   labels);
			error.addPath(path, disabledAlts.contains(displayAltI));

            /*
			int alt = displayAltI.intValue();
            if ( nfaStart.getDecisionASTNode().getType()==ANTLRParser.EOB ) {
                if ( alt==nondetMsg.probe.dfa.nfa.grammar.getNumberOfAltsForDecisionNFA(nfaStart) )
                    alt = 1;
                else
                    alt = alt+1;
            }

            // Get a list of all states (this is the error path)
            List path = nondetMsg.probe.getNFAPathStatesForAlt(nondetMsg.problemState,alt,labels);
            error.addPath(path, disabledAlts.contains(displayAltI));
            //System.out.println(displayAltI+" = "+path);
			*/

            // Find all rules enclosing each state (because a path can extend over multiple rules)
            for (Iterator iterator = path.iterator(); iterator.hasNext();) {
                NFAState state = (NFAState)iterator.next();
                error.addRule(state.getEnclosingRule());
            }
        }

        return error;
    }

    private class EngineErrorListener implements ANTLRErrorListener {
        List infos = new LinkedList();
        List errors = new LinkedList();
        List warnings = new LinkedList();

        public void clear() {
            infos.clear();
            errors.clear();
            warnings.clear();
        }

        public void info(String msg) {
            //System.out.println("info: "+msg);
            infos.add(msg);
        }

        public void error(Message msg) {
            //System.out.println("error: "+msg);
            errors.add(msg);
        }

        public void warning(Message msg) {
            //System.out.println("warning: "+msg);
            warnings.add(msg);
        }

        public void error(ToolMessage msg) {
            //System.out.println(msg);
            errors.add(msg);
        }

        public int size() {
            return infos.size() + errors.size() + warnings.size();
        }
    };

}
