import {expect, it} from 'vitest';
import {MAX_UPLOAD_BYTES, validAttachments} from '@/modules/media/model/uploads';

it('validates whole attachment batches before any network request', () => {
    const photo = new File(['image'], 'photo.jpg', {type: 'image/jpeg'});
    const video = new File(['video'], 'video.mp4', {type: 'video/mp4'});
    expect(validAttachments([photo, video], 'media')).toBe('');
    expect(validAttachments(Array(11).fill(photo), 'media')).toBe('tooMany');
    expect(validAttachments([new File(['pdf'], 'report.pdf', {type: 'application/pdf'})], 'media')).toBe(
        'unsupported',
    );
    expect(validAttachments([new File(['pdf'], 'report.pdf', {type: 'application/pdf'})], 'document')).toBe(
        '',
    );
    const large = new File(['x'], 'large.bin');
    Object.defineProperty(large, 'size', {value: MAX_UPLOAD_BYTES + 1});
    expect(validAttachments([large], 'document')).toBe('tooLarge');
    expect(validAttachments([new File([], 'empty.bin')], 'document')).toBe('tooLarge');
});
