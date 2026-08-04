package de.bht.modulebook.module;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class ModuleRepository {

    // JdbcTemplate is Spring's helper class for running SQL.
    private final JdbcTemplate jdbcTemplate;

    // SimpleJdbcInsert is configured once and reused for inserting modules.
    private final SimpleJdbcInsert insertModule;

    // Reuse one mapper for all SELECT queries that return Module rows.
    private final RowMapper<Module> moduleRowMapper = new ModuleRowMapper();

    public ModuleRepository(JdbcTemplate jdbcTemplate) {
        // Spring automatically gives this repository a JdbcTemplate object.
        this.jdbcTemplate = jdbcTemplate;
        this.insertModule = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("modules")
                .usingGeneratedKeyColumns("id");
    }

    // Inserts one Module row and returns the same object with its database id set.
    public Module insert(Module module) {
        Map<String, Object> values = new HashMap<>();

        // Map Java object fields to database column names.
        values.put("code", module.getCode());
        values.put("name", module.getName());
        values.put("credits", module.getCredits());
        values.put("description", module.getDescription());

        // PostgreSQL creates the id, so SimpleJdbcInsert returns the generated key.
        Number newId = insertModule.executeAndReturnKey(values);
        module.setId(newId.longValue());

        return module;
    }

    public Optional<Module> findById(Long id) {
        // The ? placeholder is safely replaced with the id argument by JdbcTemplate.
        List<Module> modules = jdbcTemplate.query(
                "SELECT id, code, name, credits, description, created_at, updated_at FROM modules WHERE id = ?",
                moduleRowMapper,
                id
        );

        // A matching row may not exist, so return Optional.empty() in that case.
        return modules.stream().findFirst();
    }

    // Returns all modules in a stable order
    public List<Module> findAll() {
        return jdbcTemplate.query(
                "SELECT id, code, name, credits, description, created_at, updated_at FROM modules ORDER BY code",
                moduleRowMapper
        );
    }

    // Updates editable fields and returns false when no row with this id exists.
    public boolean update(Long id, Module module) {
        int rowsUpdated = jdbcTemplate.update(
                "UPDATE modules SET code = ?, name = ?, credits = ?, description = ?, updated_at = now() WHERE id = ?",
                module.getCode(),
                module.getName(),
                module.getCredits(),
                module.getDescription(),
                id
        );
        return rowsUpdated == 1;
    }

    // Deletes one module and returns false when no row with this id exists.
    public boolean delete(Long id) {
        int rowsDeleted = jdbcTemplate.update(
                "DELETE FROM modules WHERE id = ?",
                id
        );
        return rowsDeleted == 1;
    }

    // Converts one database row from the modules table into one Java Module object.
    private static class ModuleRowMapper implements RowMapper<Module> {

        // Spring calls this method once for every row returned by a SELECT query.
        @Override
        public Module mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Module(
                    rs.getLong("id"),
                    rs.getString("code"),
                    rs.getString("name"),
                    rs.getBigDecimal("credits"),
                    rs.getString("description"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
            );
        }
    }
}
