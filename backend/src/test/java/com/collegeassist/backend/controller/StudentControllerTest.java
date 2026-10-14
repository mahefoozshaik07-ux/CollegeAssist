package com.collegeassist.backend.controller;

import com.collegeassist.backend.model.Student;
import com.collegeassist.backend.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StudentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private StudentRepository studentRepository;

    @InjectMocks
    private StudentController studentController;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(studentController)
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldReturnAllStudents() throws Exception {
        Student s1 = new Student(1L, "Alice Johnson", "alice@example.com", "Computer Science");
        Student s2 = new Student(2L, "Bob Smith", "bob@example.com", "Mechanical Engineering");

        when(studentRepository.findAll()).thenReturn(Arrays.asList(s1, s2));

        mockMvc.perform(get("/api/students"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].name", is("Alice Johnson")))
                .andExpect(jsonPath("$[0].email", is("alice@example.com")))
                .andExpect(jsonPath("$[0].department", is("Computer Science")))
                .andExpect(jsonPath("$[1].id", is(2)))
                .andExpect(jsonPath("$[1].name", is("Bob Smith")));
    }

    @Test
    void shouldReturnEmptyListWhenNoStudents() throws Exception {
        when(studentRepository.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/students"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void shouldReturnStudentByIdWhenFound() throws Exception {
        Student s1 = new Student(1L, "Alice Johnson", "alice@example.com", "Computer Science");

        when(studentRepository.findById(1L)).thenReturn(Optional.of(s1));

        mockMvc.perform(get("/api/students/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.name", is("Alice Johnson")))
                .andExpect(jsonPath("$.email", is("alice@example.com")))
                .andExpect(jsonPath("$.department", is("Computer Science")));
    }

    @Test
    void shouldReturn404WhenStudentNotFoundById() throws Exception {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/students/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Student not found with id: 99")));
    }

    @Test
    void shouldCreateStudentWhenPayloadIsValid() throws Exception {
        Student saved = new Student(1L, "Alice Johnson", "alice@example.com", "Computer Science");

        when(studentRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(studentRepository.save(any(Student.class))).thenReturn(saved);

        String jsonPayload = "{\"name\":\"Alice Johnson\",\"email\":\"alice@example.com\",\"department\":\"Computer Science\"}";

        mockMvc.perform(post("/api/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.name", is("Alice Johnson")))
                .andExpect(jsonPath("$.email", is("alice@example.com")))
                .andExpect(jsonPath("$.department", is("Computer Science")));
    }

    @Test
    void shouldReturn400WhenNameIsBlank() throws Exception {
        String jsonPayload = "{\"name\":\"\",\"email\":\"alice@example.com\",\"department\":\"Computer Science\"}";

        mockMvc.perform(post("/api/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").exists());
    }

    @Test
    void shouldReturn400WhenEmailIsInvalid() throws Exception {
        String jsonPayload = "{\"name\":\"Alice Johnson\",\"email\":\"invalid-email\",\"department\":\"Computer Science\"}";

        mockMvc.perform(post("/api/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.email").exists());
    }

    @Test
    void shouldReturn400WhenDepartmentIsBlank() throws Exception {
        String jsonPayload = "{\"name\":\"Alice Johnson\",\"email\":\"alice@example.com\",\"department\":\"   \"}";

        mockMvc.perform(post("/api/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.department").exists());
    }

    @Test
    void shouldReturn409WhenEmailAlreadyExists() throws Exception {
        when(studentRepository.existsByEmail("alice@example.com")).thenReturn(true);

        String jsonPayload = "{\"name\":\"Alice Johnson\",\"email\":\"alice@example.com\",\"department\":\"Computer Science\"}";

        mockMvc.perform(post("/api/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("Student with email 'alice@example.com' already exists")));
    }

    @Test
    void shouldDeleteStudentWhenFound() throws Exception {
        when(studentRepository.existsById(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/students/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Student deleted successfully")));

        verify(studentRepository).deleteById(1L);
    }

    @Test
    void shouldReturn404WhenDeletingNonExistentStudent() throws Exception {
        when(studentRepository.existsById(99L)).thenReturn(false);

        mockMvc.perform(delete("/api/students/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Student not found with id: 99")));
    }
}
