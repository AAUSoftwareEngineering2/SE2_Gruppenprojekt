package at.aau.kuhhandel.server.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class UserEntity(
    @Column(name = "username", nullable = false, unique = true, length = 64)
    var username: String,
    @Column(name = "password_hash", nullable = false, length = 255)
    var passwordHash: String = "",
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,
)
