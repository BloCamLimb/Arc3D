/*
 * This file is part of Arc 3D.
 *
 * Copyright (C) 2022-2023 BloCamLimb <pocamelards@gmail.com>
 *
 * Arc 3D is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Arc 3D is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Arc 3D. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.arc3d.compiler;

import icyllis.arc3d.compiler.tree.*;
import org.jetbrains.annotations.UnmodifiableView;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Thread-safe class that tracks per-thread state associated with {@link Compiler}
 * (i.e. {@link Parser} or CodeGenerator).
 */
public final class ThreadContext {

    private static final ThreadLocal<ThreadContext> TLS = new ThreadLocal<>();

    // The Context holds a pointer to the configuration of the program being compiled.
    private final ModuleKind mKind;
    private final ModuleOptions mOptions;
    private final boolean mIsBuiltin;

    // The Context holds all of the built-in types.
    private final BuiltinTypes mTypes;

    // This is the current symbol table of the code we are processing, and therefore changes during
    // compilation
    private SymbolTable mSymbolTable;
    // The element map from the parent module
    private final List<Element> mParentElements;

    private final ArrayList<Element> mUniqueElements = new ArrayList<>();
    private final ArrayList<Element> mSharedElements = new ArrayList<>();

    // The Context holds a pointer to our error handler.
    ErrorHandler mErrorHandler = new ErrorHandler() {
        @Override
        protected void handleError(int start, int end, String msg) {
            throw new RuntimeException("error: " + msg);
        }
    };

    /**
     * @param isBuiltin true if we are processing include files
     */
    ThreadContext(ModuleKind kind, ModuleOptions options,
                  Module parent, boolean isBuiltin) {
        mKind = Objects.requireNonNull(kind);
        mOptions = Objects.requireNonNull(options);
        Objects.requireNonNull(parent);
        mIsBuiltin = isBuiltin;

        mTypes = ModuleLoader.getInstance().getBuiltinTypes();

        mSymbolTable = parent.mSymbols.enterModule(isBuiltin);
        mParentElements = Collections.unmodifiableList(parent.mElements);

        TLS.set(this);
    }

    void end() {
        TLS.remove();
    }

    /**
     * Returns true if the DSL has been started.
     */
    public static boolean isActive() {
        return TLS.get() != null;
    }

    /**
     * Returns the Context on the current thread.
     *
     * @throws NullPointerException DSL is not started
     */
    public static ThreadContext getInstance() {
        return Objects.requireNonNull(TLS.get(), "DSL is not started");
    }

    public ModuleKind getKind() {
        return mKind;
    }

    public ModuleOptions getOptions() {
        return mOptions;
    }

    /**
     * @return true if we are processing include files
     */
    public boolean isBuiltin() {
        return mIsBuiltin;
    }

    /**
     * Returns the BuiltinTypes used by DSL operations in the current thread.
     */
    public BuiltinTypes getTypes() {
        return mTypes;
    }

    /**
     * Returns the current SymbolTable.
     */
    public SymbolTable getSymbolTable() {
        return mSymbolTable;
    }

    /**
     * Enters a scope level.
     */
    public void enterScope() {
        mSymbolTable = mSymbolTable.enterScope();
    }

    /**
     * Leaves a scope level.
     */
    public void leaveScope() {
        mSymbolTable = mSymbolTable.leaveScope();
    }

    /**
     * Returns the elements of the parent module, unmodifiable.
     */
    @UnmodifiableView
    public List<Element> getParentElements() {
        return mParentElements;
    }

    /**
     * Returns a list for adding unique elements in the target module.
     */
    public ArrayList<Element> getUniqueElements() {
        return mUniqueElements;
    }

    /**
     * Returns a list for adding used elements in the target module shared from {@link #getParentElements()}.
     */
    public ArrayList<Element> getSharedElements() {
        return mSharedElements;
    }

    public void error(int position, String msg) {
        mErrorHandler.error(position, msg);
    }

    public void error(int start, int end, String msg) {
        mErrorHandler.error(start, end, msg);
    }

    /**
     * Create expressions with the given identifier name and current symbol table.
     * Report errors via {@link ErrorHandler}; return null on error.
     */
    @Nullable
    public Expression convertIdentifier(int position, String name) {
        Symbol result = mSymbolTable.find(name);
        if (result == null) {
            error(position, "identifier '" + name + "' is undefined");
            return null;
        }
        return switch (result.getKind()) {
            case FUNCTION -> {
                var overloadChain = (Function) result;
                yield FunctionReference.make(position, overloadChain);
            }
            case VARIABLE -> {
                var variable = (Variable) result;
                yield VariableReference.make(position, variable,
                        VariableReference.kRead_ReferenceKind);
            }
            case ANONYMOUS_FIELD -> {
                var field = (AnonymousField) result;
                Expression base = VariableReference.make(position, field.getContainer(),
                        VariableReference.kRead_ReferenceKind);
                yield FieldExpression.make(position, base, field.getFieldIndex(),
                        /*anonymousBlock*/true);
            }
            case TYPE -> {
                var type = (Type) result;
                if (!mIsBuiltin && type.isGeneric()) {
                    error(position, "type '" + type.getName() + "' is generic");
                    type = getTypes().mPoison;
                }
                yield TypeReference.make(position, type);
            }
        };
    }
}
