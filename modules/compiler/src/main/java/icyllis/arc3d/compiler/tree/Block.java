/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2022-2024 BloCamLimb <pocamelards@gmail.com>
 *
 * Arc3D is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Arc3D is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Arc3D. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.arc3d.compiler.tree;

import icyllis.arc3d.compiler.Position;
import icyllis.arc3d.compiler.SymbolTable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;

/**
 * A block of multiple statements functioning as a single statement.
 */
public final class Block extends Statement {

    private final ArrayList<Statement> mStatements;
    private boolean mScoped;
    private final SymbolTable mSymbolTable;

    public Block(int position, ArrayList<Statement> statements, boolean scoped,
                 SymbolTable symbolTable) {
        super(position);
        mStatements = statements;
        mScoped = scoped;
        mSymbolTable = symbolTable;
    }

    // Make is allowed to simplify compound statements. For a single-statement unscoped Block,
    // Make can return the Statement as-is. For an empty unscoped Block, Make can return Nop.
    public static Statement make(int pos, @NonNull ArrayList<Statement> statements, boolean scoped,
                                 @Nullable SymbolTable symbolTable) {
        if (scoped) {
            // In most cases, the symbol table is not null
            return new Block(pos, statements, true, symbolTable);
        }

        assert symbolTable == null;

        // If the Block is completely empty, synthesize a Nop.
        if (statements.isEmpty()) {
            return new EmptyStatement(pos);
        }

        if (statements.size() > 1) {
            // The statement array contains multiple statements, but some of those might be no-ops.
            // If the statement array only contains one real statement, we can return that directly and
            // avoid creating an additional Block node.
            Statement foundStatement = null;
            for (Statement stmt : statements) {
                if (!stmt.isEmpty()) {
                    if (foundStatement == null) {
                        // We found a single non-empty statement. Remember it and keep looking.
                        foundStatement = stmt;
                        continue;
                    }
                    // We found more than one non-empty statement. We actually do need a Block.
                    return new Block(pos, statements, scoped,
                            null);
                }
            }

            // The array wrapped one valid Statement. Avoid allocating a Block by returning it directly.
            if (foundStatement != null) {
                return foundStatement;
            }

            // The statement array contained nothing but empty statements!
            // In this case, we don't actually need to allocate a Block.
            // We can just return one of those empty statements. Fall through to...
        }

        return statements.get(0);
    }

    @NonNull
    public static Block makeBlock(int pos,
                                  @NonNull ArrayList<Statement> statements,
                                  boolean scoped,
                                  @Nullable SymbolTable symbolTable) {
        return new Block(pos, statements, scoped, symbolTable);
    }

    public static Statement makeCompound(Statement before, Statement after) {
        if (before == null || before.isEmpty()) {
            return after;
        }
        if (after == null || after.isEmpty()) {
            return before;
        }

        if (before instanceof Block block && !block.isScoped()) {
            block.getStatements().add(after);
            return before;
        }

        int pos = Position.range(before.getStartOffset(), after.getEndOffset());
        ArrayList<Statement> statements = new ArrayList<>(2);
        statements.add(before);
        statements.add(after);
        return new Block(pos, statements, false, null);
    }

    @Override
    public StatementKind getKind() {
        return StatementKind.BLOCK;
    }

    @Override
    public boolean isEmpty() {
        for (Statement stmt : mStatements) {
            if (!stmt.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    public SymbolTable getSymbolTable() {
        return mSymbolTable;
    }

    public ArrayList<Statement> getStatements() {
        return mStatements;
    }

    public boolean isScoped() {
        return mScoped;
    }

    public void setScoped(boolean scoped) {
        mScoped = scoped;
    }

    @NonNull
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        boolean isScoped = isScoped() || isEmpty();
        if (isScoped) {
            result.append("{");
        }
        for (Statement stmt : mStatements) {
            result.append("\n");
            result.append(stmt.toString());
        }
        result.append(isScoped ? "\n}\n" : "\n");
        return result.toString();
    }
}
