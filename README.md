Naturalize
===============
Naturalize suggests natural names for source code.

naturalize is released under a BSD license.

The project depends on three internal (maven) modules:


a) [codemining-sequencelm](https://github.com/mast-group/codemining-sequencelm)
b) [codemining-utils](https://github.com/mast-group/codemining-utils)
c) [codemining-core](https://github.com/mast-group/codemining-core)
d) [commitmining-tools](https://github.com/mast-group/commitmining-tools)

the rest of the dependencies are declared in the maven dependencies. 



## Basic Usage
```java
final AbstractIdentifierRenamings renamer = new BaseIdentifierRenamings(
				new JavaTokenizer());
renamer.buildRenamingModel(trainingFiles);

final SortedSet<AbstractIdentifierRenamings.Renaming> renamings = renamer
						.getRenamings(new Scope(snippet,
								Scope.ScopeType.SCOPE_LOCAL, null, -1, -1),
								nameOfIdentifier);
```



If you need a method for directly specifying variable bindings consider 
using `AbstractIdentifierRenamings.getRenamings(TokenNameBinding binding)`
that avoids retokenizing the snippet and performing a textual match on the
tokens.
