package compiler;

import compiler.AST.*;
import compiler.lib.*;
import compiler.exc.*;
import visualsvm.ExecuteVM;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static compiler.lib.FOOLlib.*;

public class CodeGenerationASTVisitor extends BaseASTVisitor<String, VoidException> {

	private List<List<String>> dispatchTables = new ArrayList<>();
  CodeGenerationASTVisitor() {}
  CodeGenerationASTVisitor(boolean debug) {super(false,debug);} //enables print for debugging

	@Override
	public String visitNode(ProgLetInNode n) {
		if (print) printNode(n);
		String declCode = null;
		for (Node dec : n.declist) declCode=nlJoin(declCode,visit(dec));
		return nlJoin(
			"push 0",	
			declCode, // generate code for declarations (allocation)			
			visit(n.exp),
			"halt",
			getCode()
		);
	}

	@Override
	public String visitNode(ProgNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.exp),
			"halt"
		);
	}

	@Override
	public String visitNode(FunNode n) {
		if (print) printNode(n,n.id);
		String declCode = null, popDecl = null, popParl = null;
		for (Node dec : n.declist) {
			declCode = nlJoin(declCode,visit(dec));
			popDecl = nlJoin(popDecl,"pop");
		}
		for (int i=0;i<n.parlist.size();i++) popParl = nlJoin(popParl,"pop");
		String funl = freshFunLabel();
		putCode(
			nlJoin(
				funl+":",
				"cfp", // set $fp to $sp value
				"lra", // load $ra value
				declCode, // generate code for local declarations (they use the new $fp!!!)
				visit(n.exp), // generate code for function body expression
				"stm", // set $tm to popped value (function result)
				popDecl, // remove local declarations from stack
				"sra", // set $ra to popped value
				"pop", // remove Access Link from stack
				popParl, // remove parameters from stack
				"sfp", // set $fp to popped value (Control Link)
				"ltm", // load $tm value (function result)
				"lra", // load $ra value
				"js"  // jump to to popped address
			)
		);
		return "push "+funl;		
	}

	@Override
	public String visitNode(VarNode n) {
		if (print) printNode(n,n.id);
		return visit(n.exp);
	}

	@Override
	public String visitNode(PrintNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.exp),
			"print"
		);
	}

	@Override
	public String visitNode(IfNode n) {
		if (print) printNode(n);
	 	String l1 = freshLabel();
	 	String l2 = freshLabel();		
		return nlJoin(
			visit(n.cond),
			"push 1",
			"beq "+l1,
			visit(n.el),
			"b "+l2,
			l1+":",
			visit(n.th),
			l2+":"
		);
	}

	@Override
	public String visitNode(EqualNode n) {
		if (print) printNode(n);
	 	String l1 = freshLabel();
	 	String l2 = freshLabel();
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"beq "+l1,
			"push 0",
			"b "+l2,
			l1+":",
			"push 1",
			l2+":"
		);
	}


	@Override
	public String visitNode(LessEqualNode n) {
		if (print) printNode(n);
		String l1 = freshLabel();
		String l2 = freshLabel();
		return nlJoin(
				visit(n.left),
				visit(n.right),
				"bleq "+l1,
				"push 0",
				"b "+l2,
				l1+":",
				"push 1",
				l2+":"
		);
	}

	@Override
	public String visitNode(GreaterEqualNode n) {
		if (print) printNode(n);
		String l1 = freshLabel();
		String l2 = freshLabel();
		String l3 = freshLabel();
		String l4 = freshLabel();
		return nlJoin(
				visit(n.left),
				visit(n.right),
				"stm",
				"ltm",
				"beq "+l1,	//se sono uguali allora restituisco true e salto alla fine (l4)
				"b "+l2,
				l1+":",
				"push 1",
				"b "+l4,
				l2+":",
				"ltm",
				"bleq "+l3, //ricarico il secondo elemento e vedo se il primo è minore del secondo, se lo è allora restituisco false
				"push 1",	//se non lo è vuol dire che il primo è strettamente maggiore del secondo e restituisco true
				"b "+l4,
				l3+":",
				"push 0",
				l4+":",
				"push 0",	//reset tm
				"stm"
		);
	}

	@Override
	public String visitNode(TimesNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"mult"
		);	
	}

	@Override
	public String visitNode(DivNode n) {
		if (print) printNode(n);
		return nlJoin(
				visit(n.left),
				visit(n.right),
				"div"
		);
	}

	@Override
	public String visitNode(PlusNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"add"				
		);
	}

	@Override
	public String visitNode(MinusNode n) {
		if (print) printNode(n);
		return nlJoin(
				visit(n.left),
				visit(n.right),
				"sub"
		);
	}

	@Override
	public String visitNode(IdNode n) {
		if (print) printNode(n,n.id);
		String getAR = null;
		for (int i = 0;i<n.nl-n.entry.nl;i++) getAR=nlJoin(getAR,"lw");
		return nlJoin(
			"lfp", getAR, // retrieve address of frame containing "id" declaration
			              // by following the static chain (of Access Links)
			"push "+n.entry.offset, "add", // compute address of "id" declaration
			"lw" // load value of "id" variable
		);
	}

	@Override
	public String visitNode(AndNode n) {
		if (print) printNode(n);
		String l1 = freshLabel();
		String l2 = freshLabel();
		String l3 = freshLabel();
		return nlJoin(
				visit(n.left),
				visit(n.right),
				"stm",
				"ltm",
				"push 0",       //confronta se il secondo termine è false
				"beq "+l1, 		//so che il secondo termine è uguale a false quindi il risultato dell'and è false
				"ltm",			//se non lo è allora il secondo termine è true (1) e lo moltiplico per il primo
				"mult",
				"push 1",
				"beq "+l3,		//se il risultato è uguale a 1 vuol dire che avevo true && true
								//se il risultato è uguale a 0 vuol dire che avevo false && true
				l1+":",			//so che uno dei due termini è uguale a false
				"push 0",		//quindi il risultato dell'and è false
				"b "+l2,
				l3+":",
				"push 1",
				l2+":",
				"push 0",		//reset tm
				"stm"
		);
	}


	@Override
	public String visitNode(OrNode n) {
		if (print) printNode(n);
		String l1 = freshLabel();
		String l2 = freshLabel();
		String l3 = freshLabel();
		return nlJoin(
				visit(n.left),
				visit(n.right),
				"stm",
				"ltm",
				"push 1",       //confronta se il secondo termine è true
				"beq "+l1, 		//so che il secondo termine è uguale a true quindi il risultato dell'or è true
				"ltm",			//se non lo è allora il secondo termine è false (0) e lo moltiplico per il primo
				"mult",
				"push 0",
				"beq "+l3,		//se il risultato è uguale a 0 vuol dire che avevo false || false
								//se il risultato è uguale a 1 vuol dire che avevo true || false
				l1+":",			//so che uno dei due termini è uguale a false
				"push 1",		//quindi il risultato dell'or è false
				"b "+l2,
				l3+":",
				"push 0",
				l2+":",
				"push 0",		//reset tm
				"stm"
		);
	}

	@Override
	public String visitNode(NotNode n) {
		if (print) printNode(n,n.exp.toString());
		String l1 = freshLabel();
		return nlJoin(
				visit(n.exp),
				"push 0",
				"beq "+l1, //l'espressione restituiva false, quindi pusho true alla label l1
				"push 0", //altrimenti restituiva true, quindi pusho false
				l1+":",
				"push 1"
		);
	}

	@Override
	public String visitNode(BoolNode n) {
		if (print) printNode(n,n.val.toString());
		return "push "+(n.val?1:0);
	}

	@Override
	public String visitNode(IntNode n) {
		if (print) printNode(n,n.val.toString());
		return "push "+n.val;
	}

	//################################      OBJECT ORIENTATION EXTENSION      ######################################

	@Override
	public String visitNode(MethodNode n) {
		if (print) printNode(n,n.id);
		String declCode = null, popDecl = null, popParl = null;
		for (Node dec : n.declist) {
			declCode = nlJoin(declCode,visit(dec));
			popDecl = nlJoin(popDecl,"pop");
		}
		for (int i=0;i<n.parlist.size();i++) popParl = nlJoin(popParl,"pop");
		n.label = freshLabel();
		putCode(
				nlJoin(
						n.label+":",
						"cfp", // set $fp to $sp value
						"lra", // load $ra value
						declCode, // generate code for local declarations (they use the new $fp!!!)
						visit(n.exp), // generate code for function body expression
						"stm", // set $tm to popped value (function result)
						popDecl, // remove local declarations from stack
						"sra", // set $ra to popped value
						"pop", // remove Access Link from stack
						popParl, // remove parameters from stack
						"sfp", // set $fp to popped value (Control Link)
						"ltm", // load $tm value (function result)
						"lra", // load $ra value
						"js"  // jump to popped address
				)
		);
		return null;
	}

	public String visitNode(ClassNode n) {
		if (print) printNode(n,n.id);;
		List<String> dt = new ArrayList<>();
		for (MethodNode method : n.methods) dt.add("err");
		dispatchTables.add(dt);
		for(MethodNode method : n.methods){
			visit(method);
			dt.set(method.offset, method.label);
		}
		String methodCode = "";
		for(String label : dt){
			methodCode = nlJoin(methodCode,
								"push "+label,		//per ciascuna etichetta
								"lhp",
								"sw",				//la memorizzo a indirizzo in $hp
								"lhp",
								"push 1",
								"add",				//ed incremento $hp
								"shp"
								);
		}

		return nlJoin(
				"lhp",			//metto valore di $hp sullo stack: sarà il dispatch pointer da ritornare alla fine
				methodCode
		);
	}

	public String visitNode(EmptyNode n){
		if (print) printNode(n);
		return "push -1";
	}

	@Override
	public String visitNode(ClassCallNode n) {
		if (print) printNode(n,n.varId+"."+n.methodId);
		String argCode = null, getAR = null;
		for (int i=n.arglist.size()-1;i>=0;i--) argCode=nlJoin(argCode,visit(n.arglist.get(i)));
		for (int i = 0;i<n.nl-n.entry.nl;i++) getAR=nlJoin(getAR,"lw");
		return nlJoin(
				"lfp", // load Control Link (pointer to frame of function "id" caller)
				argCode, // generate code for argument expressions in reversed order
				"lfp", getAR, // retrieve address of frame containing "id" declaration by following the static chain (of Access Links)
				"push "+n.entry.offset, "add", //compute address of ID1.
				"lw",
				"stm", // set $tm to popped value (with the aim of duplicating top of stack)
				"ltm", // load Access Link (pointer to frame of function "id" declaration)
				"ltm", // duplica la cima dello stack
				"lw",  // vado nella dispatch table della classe dell'oggetto seguendo il dispatch pointer (che è uguale all'object pointer)
				"push "+n.methodEntry.offset, "add", // trovo l'indirizzo della dichiarazione dell'n-esimo metodo nella dispatch table
				"lw", // e lo seguo
				"js"  // jump to popped address (saving address of subsequent instruction in $ra)
		);
	}

	@Override
	public String visitNode(CallNode n) {
		if (print) printNode(n,n.id);
		String argCode = null, getAR = null;
		for (int i=n.arglist.size()-1;i>=0;i--) argCode=nlJoin(argCode,visit(n.arglist.get(i)));
		for (int i = 0;i<n.nl-n.entry.nl;i++) getAR=nlJoin(getAR,"lw");
		String retrieveFun;
		if(n.entry.type instanceof MethodTypeNode){
			retrieveFun = nlJoin("lw",  						// vado nella dispatch table della classe dell'oggetto seguendo il dispatch pointer (che è uguale all'object pointer)
								 "push "+n.entry.offset, "add", // trovo l'indirizzo della dichiarazione dell'n-esimo metodo nella dispatch table
								 "lw", 							// e lo seguo
								 "js");  						// jump to popped address (saving address of subsequent instruction in $ra);
		} else {
			retrieveFun = nlJoin("push "+n.entry.offset, "add", // compute address of "id" declaration
								 "lw", // load address of "id" function
								 "js"  // jump to popped address (saving address of subsequent instruction in $ra)
			);
		}
		return nlJoin(
				"lfp", // load Control Link (pointer to frame of function "id" caller)
				argCode, // generate code for argument expressions in reversed order
				"lfp", getAR, // retrieve address of frame containing "id" declaration
				// by following the static chain (of Access Links)
				"stm", // set $tm to popped value (with the aim of duplicating top of stack)
				"ltm", // load Access Link (pointer to frame of function "id" declaration)
				"ltm", // duplicate top of stack
				retrieveFun
		);
	}

	@Override
	public String visitNode(NewNode n) {
		if (print) printNode(n);
		String argCode = "";
		String pushArgs = "";
		int pos = ExecuteVM.MEMSIZE+n.entry.offset;
		for(Node arg:n.arglist){

			//prima: si richiama su tutti gli argomenti in ordine di apparizione (che mettono ciascuno il loro valore calcolato sullo stack)
			argCode = nlJoin(argCode, visit(arg));

			//poi: prende i valori degli argomenti, uno alla volta, dallo stack e li mette nello heap, incrementando $hp dopo ogni singola copia
			pushArgs = nlJoin(pushArgs,			//per ogni parametro della new
							"lhp",				//prendo il valore del prossimo argomento sulla pila
							"sw",				//la memorizzo a indirizzo in $hp
							"lhp",
							"push 1",
							"add",				//ed incremento $hp
							"shp");
		}
		return nlJoin(
				argCode,
				pushArgs,
				"push "+ pos,
				"lw",
				"lhp",
				"sw",		//scrive a indirizzo $hp il dispatch pointer recuperandolo da contenuto indirizzo MEMSIZE + offset classe ID
				"lhp",		//carica sullo stack il valore di $hp (indirizzo object pointer da ritornare) e incrementa $hp
				"lhp",
				"push 1",
				"add",		//incremento $hp
				"shp"
		);

	}
}