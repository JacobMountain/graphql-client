package co.uk.jacobmountain;

import co.uk.jacobmountain.exceptions.SchemaNotFoundException;
import co.uk.jacobmountain.utils.Schema;
import com.google.auto.service.AutoService;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import lombok.SneakyThrows;
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
import java.util.Set;

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

    @Override
    @SneakyThrows
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element el : roundEnv.getElementsAnnotatedWith(GraphQLClient.class)) {
            if (!(el.getKind() == ElementKind.CLASS || el.getKind() == ElementKind.INTERFACE)) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Can't be applied to class");
                return true;
            }

            TypeElement client = (TypeElement) el;

            GraphQLClient annotation = client.getAnnotation(GraphQLClient.class);

            String packageName = getPackage(client);

            Schema schema = readSchema(annotation);

            TypeMapper typeMapper = new TypeMapper(packageName + ".dto", annotation.mapping());

            log.info("Generating java classes from GraphQL schema");
            DTOGenerator dtoGenerator = new DTOGenerator(packageName + ".dto", new FileWriter(this.filer), typeMapper);
            dtoGenerator.generate(schema.types().values());
            dtoGenerator.generateArgumentDTOs(client);

            log.info("Generating java implementation of {}", client.getSimpleName());
            new ClientGenerator(this.filer, annotation.maxDepth(), typeMapper, packageName)
                    .generate(schema, client, annotation.implSuffix());
            return true;
        }
        return false;
    }

    private String getPackage(TypeElement e) {
        return processingEnv.getElementUtils().getPackageOf(e).toString();
    }

    private Schema readSchema(GraphQLClient annotation) {
        String value = annotation.schema();
        try {
            if (!value.trim().equals("")) {
                File file = getRoot().resolve(value)
                        .toAbsolutePath()
                        .toFile();
                log.info("Reading schema {}", file);
                TypeDefinitionRegistry registry = new SchemaParser().parse(file);
                return new Schema(registry);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        throw new SchemaNotFoundException();
    }

    @SneakyThrows
    private Path getRoot() {
        FileObject resource = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "tmp", (Element[]) null);
        Path projectPath = Paths.get(resource.toUri()).getParent().getParent().getParent().getParent().getParent();
        resource.delete();
        return projectPath;
    }

}
