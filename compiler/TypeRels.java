package compiler;

import compiler.AST.*;
import compiler.lib.*;

public class TypeRels {

	// valuta se il tipo "a" e' <= al tipo "b", dove "a" e "b" sono tipi di base IntTypeNode o BoolTypeNode oppure
	// tipo EmptyTypeNode sottotipo di un qualsiasi tipo riferimento RefTypeNode
	public static boolean isSubtype(TypeNode a, TypeNode b) {
		return 	   a.getClass().equals(b.getClass())
				|| ((a instanceof BoolTypeNode) && (b instanceof IntTypeNode))
				|| ((a instanceof EmptyTypeNode) && (b instanceof RefTypeNode));
	}

}
