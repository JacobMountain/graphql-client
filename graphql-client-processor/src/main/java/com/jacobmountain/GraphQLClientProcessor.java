package com.jacobmountain;

import com.google.auto.service.AutoService;
import com.jacobmountain.exceptions.SchemaNotFoundException;
import com.jacobmountain.utils.Schema;
import com.jacobmountain.utils.StringUtils;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Consumer;

@Slf4j
@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes("co.uk.jacobmountain.*")
public class GraphQLClientProcessor extends AbstractProcessor {

    private Filer filer;

    private Messager messager;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.filer = processingEnv.getFiler();
        this.messager = processingEnv.getMessager();
    }

    private Path root;

    @Override
    @SneakyThrows
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(GraphQLClient.class);
        for (Element el : elements) {
            if (!(el.getKind() == ElementKind.CLASS || el.getKind() == ElementKind.INTERFACE)) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Can't be applied to class");
                return true;
            }
        }
        return elements.stream()
                .map(el -> (TypeElement) el)
                .map(Input::new)
                .peek(this.generateJavaDataClasses())
                .peek(this::generateClientImplementation)
                .count() > 0;
    }

    private Consumer<Input> generateJavaDataClasses() {
        return input -> {
            log.info("Generating java classes from GraphQL schema");
            DTOGenerator dtoGenerator = new DTOGenerator(input.getDtoPackage(), new FileWriter(this.filer), input.getTypeMapper());
            dtoGenerator.generate(input.getSchema().types().values());
            dtoGenerator.generateArgumentDTOs(input.element);
        };
    }

    private void generateClientImplementation(Input client) {
        GraphQLClient annotation = client.getAnnotation();
        log.info("Generating java implementation of {}", client.element.getSimpleName());
        new ClientGenerator(this.filer, annotation.maxDepth(), client.getTypeMapper(), client.getPackage(), client.getDtoPackage(), client.getSchema(), annotation.reactive())
                .generate(client.element, annotation.implSuffix());
    }

    @SneakyThrows
    private Path getRoot() {
        if (root == null) {
            FileObject resource = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "tmp", (Element[]) null);
            root = Paths.get(resource.toUri()).getParent().getParent().getParent().getParent().getParent();
            resource.delete();
        }
        return root;
    }

    @Value
    @AllArgsConstructor
    private class Input {

        TypeElement element;

        GraphQLClient getAnnotation() {
            return element.getAnnotation(GraphQLClient.class);
        }

        TypeMapper getTypeMapper() {
            return new TypeMapper(getDtoPackage(), getAnnotation().mapping());
        }

        String getDtoPackage() {
            return String.join(".", Arrays.asList(
                    getPackage(),
                    getAnnotation().dtoPackage()
            ));
        }

        String getPackage() {
            return processingEnv.getElementUtils().getPackageOf(element).toString();
        }

        Schema getSchema() {
            String value = getAnnotation().schema();
            try {
                if (StringUtils.hasLength(value)) {
                    File file = getSchemaFile();
                    log.info("Reading schema {}", file);
                    return new Schema(getSchemaFile());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            throw new SchemaNotFoundException();
        }

        File getSchemaFile() {
            return getRoot().resolve(getAnnotation().schema())
                    .toAbsolutePath()
                    .toFile();
        }

    }

}
