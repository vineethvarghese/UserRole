package com.userrole.role;

import jakarta.persistence.*;

@Entity
@Table(
        name = "permissions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_permissions_role_action_entity",
                columnNames = {"role_id", "action", "entity_type_id"}
        )
)
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Action action;

    @Column(name = "entity_type_id", nullable = false)
    private Long entityTypeId;

    protected Permission() {
    }

    public Permission(Role role, Action action, Long entityTypeId) {
        this.role = role;
        this.action = action;
        this.entityTypeId = entityTypeId;
    }

    public Long getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    public Action getAction() {
        return action;
    }

    public Long getEntityTypeId() {
        return entityTypeId;
    }
}
