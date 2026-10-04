package bf.ambawbio.shared.web;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** Page de résultats renvoyée par l'API ({@code ?page=&taille=}). */
public record PageResultat<T>(List<T> elements, int page, int taille, long total) {

    public static <E, T> PageResultat<T> de(Page<E> page, Function<E, T> conversion) {
        return new PageResultat<>(page.getContent().stream().map(conversion).toList(), page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
