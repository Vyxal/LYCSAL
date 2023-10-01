So you've decided to help <s>me</s> us with LYCSAL. Good choice! You definitely won't regret it. Keep these things in mind while developing:
1. LYCSAL is an exciting, next-generation compiler, and as such is subject to exciting, next-generation crashes. Segfaults are par for the course here.
2. Relatedly: GDB is not your friend. If you can do any useful debugging with it, please ping me and explain how; otherwise, take a reeeeeal good look at the arguments you're passing to the problematic function.
3. LLVM is byzantine and difficult to understand; JavaCPP even more so. Read the documentation and examples extremely carefully.

You'll probably find these resources valuable:
* [The LLVM IR reference](https://www.llvm.org/docs/LangRef.html). Invaluable for figuring out what instructions you have to play with.
* [The LLVM JavaCPP docs](http://bytedeco.org/javacpp-presets/llvm/apidocs/). They're pretty long and lacking in actual documentation info, but nice for finding functions.
* [The LLVM JavaCPP examples](https://github.com/bytedeco/javacpp-presets/tree/master/llvm). They're about all you'll get in way of examples, so read them well.
* [Godbolt](https://godbolt.org/) is sometimes nice for figuring out how the Big Boys™ implement things.

Regarding the design of LYCSAL itself:
* LYCSAL piggybacks off of Vyxal 3's lexer and parser, which is why you won't find anything to do that here. The target Vyxal 3 JAR is in `lib/`. Make sure that the versions of the dependencies in `build.sbt` match this JAR's dependencies, to make `sbt assembly`'s life easier.
* `LYCSAL.scala` and `CLI.scala` are mostly wrappers and such for running the compiler. You shouldn't need to mess with them much.
* `Compiler.scala` contains the dark, brooding soul of the compiler. It's not important for adding elements, but if you're adding new AST handlers they go here.
* `Literals.scala` and `TypeSupplier`.scala are concerned with LLVM types. When adding new elements, make sure to use the provided `TypeSupplier`'s types instead of calling LLVM API functions to make new ones! `TypeSupplier.scala` also contains `TypeSupplier.libc`, which is where libc externs go. If you need `malloc()` or such, you're almost definitely doing something wrong, which is why I intentionally didn't include them. If you're _sure_ you need them, ping me and I can add them back in.
* `Mechanism.scala` contains miscellaneous machinery used by the compiler. Any common utility functions _used at compile-time_ can go here.
* `Runtime.scala` contains _runtime_ utility function implementations.
* `Elements.scala` is where the elements go. Hopefully there's enough code there already for it to be easy to add new elements.
* You should never need to do anything with `ElementHelpers.scala` (which is roughly equivalent to Vyxal 3's `Functions.scala`), `PointerStack.scala` or `Exceptions.scala`.

Regarding the implementation of elements:
* Always remember that you're not writing code to execute an element; you're writing code that writes code to execute an element.
* Non-direct elements CANNOT directly modify the stack! Do not EVER do that!
* Direct elements should be used with extreme caution, and should not directly push to the stack. Any values you want to add to the stack should be returned.
* If your implementation messes with the builder's position, make sure you don't leave it somewhere stupid.
* Overloads should be directly implemented inside the element with `match`es.

If you have any questions, don't hesitate to ping @Ginger in the Vyxal chatroom.
